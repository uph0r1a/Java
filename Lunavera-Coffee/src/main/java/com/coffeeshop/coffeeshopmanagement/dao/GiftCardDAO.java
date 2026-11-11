package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.GiftCard;
import com.coffeeshop.coffeeshopmanagement.service.GiftCardLookupGuard;
import com.coffeeshop.coffeeshopmanagement.service.GiftCardRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Gift cards (TODO 7, rule D23 in {@link GiftCardRules}). Anybody signed in may sell a card or look at a balance (it is a till job).
 * Spending and giving back happen on the connection of the payment or the refund ({@link #redeem}, {@link #restoreForOrder}), so they
 * commit or roll back with the sale; the balance is never changed without a line in {@code gift_card_moves}, and a spend is one guarded
 * UPDATE, so two tills cannot spend the same money twice.
 */
public class GiftCardDAO {

    private static final String COLUMNS = "id, code, initial_amount, balance, sold_by, created_at";

    /** Sells a new card for {@code amount} whole dong (taken in cash by the caller) and returns it with its code. One audit line. */
    public GiftCard sell(long amount) {
        if (Session.getCurrentUser() == null) throw new DataAccessException(I18n.t("admin.only.message"));
        if (!GiftCardRules.validAmount(amount)) {
            throw new DataAccessException(I18n.t("giftcard.val.amount", CurrencyUtil.format(BigDecimal.valueOf(GiftCardRules.MIN_AMOUNT)),
                    CurrencyUtil.format(BigDecimal.valueOf(GiftCardRules.MAX_AMOUNT)), CurrencyUtil.format(BigDecimal.valueOf(GiftCardRules.STEP))));
        }
        String seller = Session.getDisplayName();
        Integer sellerId = Session.getCurrentEmployee() == null ? null : Session.getCurrentEmployee().getId(); // for TODO 2.87
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LocalDateTime now = AppClock.now();
                String code = null;
                int id = 0;
                for (int attempt = 0; attempt < 5 && id == 0; attempt++) { // a clash of two random codes is almost impossible; try again if it happens
                    code = GiftCardRules.newCode();
                    if (find(connection, code).isPresent()) continue;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO gift_cards (code, initial_amount, balance, sold_by, created_at, sold_by_id) VALUES (?, ?, ?, ?, ?, ?)",
                            java.sql.Statement.RETURN_GENERATED_KEYS)) {
                        statement.setString(1, code);
                        statement.setLong(2, amount);
                        statement.setLong(3, amount);
                        statement.setString(4, seller == null || seller.isBlank() ? "?" : seller);
                        statement.setString(5, DbTime.write(now));
                        if (sellerId == null) statement.setNull(6, java.sql.Types.INTEGER);
                        else statement.setInt(6, sellerId);
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            if (keys.next()) id = keys.getInt(1);
                        }
                    }
                }
                if (id == 0) throw new DataAccessException("Failed to make a gift card code");
                move(connection, id, null, amount, now);
                AuditDAO.write(connection, AuditAction.GIFT_CARD_SOLD, AuditTarget.GIFT_CARD, id, code, null, String.valueOf(amount), "cash");
                GiftCard card = find(connection, code).orElseThrow();
                connection.commit();
                return card;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to sell a gift card", e);
        }
    }

    /** One card in the administrator's list (TODO 4.45): the card, when it was last spent from (null = never) and whether it was voided. */
    public record Row(GiftCard card, LocalDateTime lastUse, boolean voided) {
    }

    /** What the shop still owes on cards: how many cards hold money, and how much. */
    public record OpenBalance(int cards, long amount) {
    }

    /** The newest {@code limit} cards, newest first (administrator only). */
    public java.util.List<Row> findAll(int limit) {
        Permissions.requireAdmin("list the gift cards");
        String sql = "SELECT c.id, c.code, c.initial_amount, c.balance, c.sold_by, c.created_at"
                + ", (SELECT MAX(m.moved_at) FROM gift_card_moves m WHERE m.card_id = c.id AND m.order_id IS NOT NULL) AS last_use, "
                + "(SELECT COUNT(*) FROM gift_card_moves v WHERE v.card_id = c.id AND v.order_id IS NULL AND v.amount < 0) AS voided "
                + "FROM gift_cards c ORDER BY c.id DESC LIMIT ?";
        java.util.List<Row> rows = new java.util.ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    GiftCard card = new GiftCard(rs.getInt(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getString(5), DbTime.parse(rs.getString(6)));
                    String last = rs.getString(7);
                    rows.add(new Row(card, last == null ? null : DbTime.parse(last), rs.getInt(8) > 0));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list the gift cards", e);
        }
        return rows;
    }

    /** The money still on cards (balance of every card; a voided card holds none). Any signed-in person may see it, like a single balance. */
    public OpenBalance openBalance() {
        try (Connection connection = DatabaseConfig.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*), COALESCE(SUM(balance), 0) FROM gift_cards WHERE balance > 0");
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return new OpenBalance(rs.getInt(1), rs.getLong(2));
        } catch (SQLException e) {
            throw new DataAccessException("Failed to total the gift cards", e);
        }
    }

    /**
     * Voids a card sold by mistake (administrator only): allowed only while nothing was ever spent from it. The balance goes to zero in one
     * guarded UPDATE, a move of minus the amount (no order) is written, so the shift close takes the cash back out of the drawer
     * ({@link #soldBetween}: the cash goes back to the customer), and one audit line is written. Refused with a sentence otherwise.
     */
    public GiftCard voidCard(String typedCode) {
        Permissions.requireAdmin("void a gift card");
        String code = GiftCardRules.normalise(typedCode);
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                GiftCard card = code == null ? null : find(connection, code).orElse(null);
                if (card == null) throw new DataAccessException(I18n.t("giftcard.unknown"));
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE gift_cards SET balance = 0 WHERE id = ? AND balance = initial_amount AND balance > 0 "
                                + "AND NOT EXISTS (SELECT 1 FROM gift_card_moves m WHERE m.card_id = gift_cards.id AND m.order_id IS NOT NULL)")) {
                    statement.setInt(1, card.id());
                    if (statement.executeUpdate() != 1) throw new DataAccessException(I18n.t("giftcard.void.refused"));
                }
                move(connection, card.id(), null, -card.balance(), AppClock.now());
                AuditDAO.write(connection, AuditAction.GIFT_CARD_VOIDED, AuditTarget.GIFT_CARD, card.id(), card.code(),
                        String.valueOf(card.balance()), "0", "cash back");
                GiftCard after = find(connection, card.code()).orElseThrow();
                connection.commit();
                return after;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to void the gift card", e);
        }
    }

    private static final GiftCardLookupGuard THIS_TILL = new GiftCardLookupGuard();

    private final GiftCardLookupGuard guard;

    public GiftCardDAO() {
        this(THIS_TILL);
    }

    public GiftCardDAO(GiftCardLookupGuard guard) {
        this.guard = guard;
    }

    /**
     * The card with this code (as typed: it is tidied first), if there is one. A text that cannot be one of our codes is empty and
     * counts for nothing. A well-formed code that does not exist is counted and logged (TODO 2A.45); after
     * {@value GiftCardLookupGuard#MAX_WRONG} of them within a minute the lookups on this till answer with a "wait" message (a
     * {@link DataAccessException}, shown by both screens) without reading the database, and one audit line says so.
     */
    public Optional<GiftCard> findByCode(String typed) {
        String code = GiftCardRules.normalise(typed);
        if (code == null) return Optional.empty();
        long wait = guard.secondsToWait();
        if (wait > 0) throw new DataAccessException(I18n.t("giftcard.wait", wait));
        Optional<GiftCard> found;
        try (Connection connection = DatabaseConfig.getConnection()) {
            found = find(connection, code);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read a gift card", e);
        }
        if (found.isEmpty()) noteUnknownCode(typed);
        return found;
    }

    private void noteUnknownCode(String typed) {
        SecurityLog.warnLimited("giftcard-unknown", "unknown gift-card code typed: " + SecurityLog.redactTyped(typed) + " by " + SecurityLog.actor());
        if (guard.recordWrong()) {
            SecurityLog.warn("gift-card lookups paused for " + GiftCardLookupGuard.PAUSE_SECONDS + " seconds after "
                    + GiftCardLookupGuard.MAX_WRONG + " unknown codes within " + GiftCardLookupGuard.WINDOW_SECONDS + " seconds; signed in: "
                    + SecurityLog.actor());
            AuditDAO.recordQuietly(AuditAction.GIFT_CARD_GUESSING, AuditTarget.GIFT_CARD, null, null, null, null,
                    GiftCardLookupGuard.MAX_WRONG + "|" + GiftCardLookupGuard.PAUSE_SECONDS);
        }
    }

    /**
     * Takes {@code amount} whole dong off the card on the payment's connection and writes the move for {@code orderId}. Refused when
     * the card does not exist or holds less (the guarded UPDATE is what stops two tills spending the same money).
     */
    public static void redeem(Connection connection, String typedCode, BigDecimal amount, int orderId) throws SQLException {
        String code = GiftCardRules.normalise(typedCode);
        long dong = amount == null ? 0 : Money.toDong(amount);
        if (code == null || dong <= 0) throw new DataAccessException(I18n.t("giftcard.unknown"));
        Optional<GiftCard> card = find(connection, code);
        if (card.isEmpty()) throw new DataAccessException(I18n.t("giftcard.unknown"));
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE gift_cards SET balance = balance - ? WHERE id = ? AND balance >= ?")) {
            statement.setLong(1, dong);
            statement.setInt(2, card.get().id());
            statement.setLong(3, dong);
            if (statement.executeUpdate() != 1) {
                throw new DataAccessException(I18n.t("giftcard.short", CurrencyUtil.format(Money.fromDong(card.get().balance()))));
            }
        }
        move(connection, card.get().id(), orderId, -dong, AppClock.now());
    }

    /**
     * The cards {@code orderId} paid with, for its receipt (TODO 5.51): per card the last four characters of the code, what the order spent
     * on it (net of what was given back) and what was left right after the spend. The balance is worked out from the moves, not read
     * from today's balance: the card's starting amount plus the moves of orders up to this order's last spend on it, so a reprint a
     * month later says the same thing. Empty for an order that paid no card.
     */
    public java.util.List<com.coffeeshop.coffeeshopmanagement.model.GiftCardUse> usesForOrder(int orderId) {
        String sql = "SELECT g.code, g.initial_amount, m.card_id, -SUM(m.amount) AS spent, MAX(m.id) AS last_move "
                + "FROM gift_card_moves m JOIN gift_cards g ON g.id = m.card_id "
                + "WHERE m.order_id = ? AND m.amount < 0 GROUP BY m.card_id, g.code, g.initial_amount ORDER BY m.card_id";
        String leftSql = "SELECT COALESCE(SUM(amount), 0) FROM gift_card_moves WHERE card_id = ? AND order_id IS NOT NULL AND id <= ?";
        java.util.List<com.coffeeshop.coffeeshopmanagement.model.GiftCardUse> uses = new java.util.ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             PreparedStatement leftStatement = connection.prepareStatement(leftSql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long spent = rs.getLong("spent");
                    leftStatement.setInt(1, rs.getInt("card_id"));
                    leftStatement.setInt(2, rs.getInt("last_move"));
                    long left;
                    try (ResultSet sum = leftStatement.executeQuery()) {
                        sum.next();
                        left = rs.getLong("initial_amount") + sum.getLong(1);
                    }
                    uses.add(new com.coffeeshop.coffeeshopmanagement.model.GiftCardUse(
                            com.coffeeshop.coffeeshopmanagement.model.GiftCardUse.tailOf(rs.getString("code")),
                            Money.fromDong(spent), Money.fromDong(Math.max(0, left))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the gift cards of an order", e);
        }
        return uses;
    }

    /** Puts back on each card what {@code orderId} spent from it and has not yet been given back; nothing for an order that paid no card. */
    public static void restoreForOrder(Connection connection, int orderId) throws SQLException {
        restoreUpTo(connection, orderId, Long.MAX_VALUE);
    }

    /**
     * What {@code orderId} spent on cards and has not had put back yet, per card ({@code [card id, dong]}, only cards with something
     * left). It is the NET of the moves of the order: a spend is negative, every give-back (a whole refund, or a line refund of
     * TODO 2.93) positive, so what is given back in parts can never add up to more than was spent.
     */
    private static java.util.List<long[]> spentByCard(Connection connection, int orderId) throws SQLException {
        java.util.List<long[]> spent = new java.util.ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT card_id, SUM(amount) FROM gift_card_moves WHERE order_id = ? GROUP BY card_id HAVING SUM(amount) < 0 ORDER BY card_id")) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) spent.add(new long[]{rs.getInt(1), -rs.getLong(2)});
            }
        }
        return spent;
    }

    /** How much the order still has spent on cards, in whole dong: the most a line refund can put back on them (0 for an order that paid no card). */
    public static long spentNet(Connection connection, int orderId) throws SQLException {
        long sum = 0;
        for (long[] row : spentByCard(connection, orderId)) sum += row[1];
        return sum;
    }

    /**
     * Puts back on the cards of {@code orderId} up to {@code max} dong of what it spent: in proportion to what each card has spent on it
     * ({@link GiftCardRules#shares}), never more on a card than that, with a move for each (TODO 2.93). Returns what was put back, which is
     * less than {@code max} only when the order has less than that left on its cards. On the connection of the refund, so it commits or
     * rolls back with it.
     */
    public static long restoreUpTo(Connection connection, int orderId, long max) throws SQLException {
        java.util.List<long[]> spent = spentByCard(connection, orderId);
        long[] caps = new long[spent.size()];
        for (int i = 0; i < caps.length; i++) caps[i] = spent.get(i)[1];
        long[] shares = GiftCardRules.shares(max, caps);
        long back = 0;
        for (int i = 0; i < shares.length; i++) {
            if (shares[i] <= 0) continue;
            try (PreparedStatement update = connection.prepareStatement("UPDATE gift_cards SET balance = balance + ? WHERE id = ?")) {
                update.setLong(1, shares[i]);
                update.setLong(2, spent.get(i)[0]);
                update.executeUpdate();
            }
            move(connection, (int) spent.get(i)[0], orderId, shares[i], AppClock.now());
            back += shares[i];
        }
        return back;
    }

    /** The cash taken for cards sold in {@code [from, to)}, less the cash given back for cards voided then (TODO 4.45): it is in the drawer but is no sale, so the shift close adds it. */
    static BigDecimal soldBetween(Connection connection, LocalDateTime from, LocalDateTime to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM gift_card_moves WHERE order_id IS NULL AND moved_at >= ? AND moved_at < ?")) {
            statement.setString(1, DbTime.write(from));
            statement.setString(2, DbTime.write(to));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Money.fromDong(rs.getLong(1)) : BigDecimal.ZERO;
            }
        }
    }

    private static void move(Connection connection, int cardId, Integer orderId, long amount, LocalDateTime at) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO gift_card_moves (card_id, order_id, amount, moved_at) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, cardId);
            if (orderId == null) statement.setNull(2, java.sql.Types.INTEGER); else statement.setInt(2, orderId);
            statement.setLong(3, amount);
            statement.setString(4, DbTime.write(at));
            statement.executeUpdate();
        }
    }

    private static Optional<GiftCard> find(Connection connection, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM gift_cards WHERE code = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(new GiftCard(rs.getInt(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getString(5),
                        DbTime.parse(rs.getString(6)))) : Optional.empty();
            }
        }
    }
}
