package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.PromotionDAO;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Applied;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Given;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Line;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Promotion;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Brings the shop's automatic promotions (TODO 4.55) to a bill: reads the promotions and the categories of the products, asks
 * {@link AutoPromotionRules} what each line gets at this moment, and puts the answer on the lines. Used twice with the same inputs:
 * by the payment window to show the cashier the saving, and by {@code CheckoutService} when the order is paid, where it is worked out
 * again from the shop's own list and the clock and never taken from the screen.
 *
 * <p>When it is worked out: <b>at payment</b>, not when a line is added. A combo or a buy-two-get-one depends on the whole bill, so it
 * cannot be known line by line, and a table's order can stay open across the end of a happy hour; the bill is priced as it is paid.
 * If a promotion ends between the payment window opening and the payment being confirmed the amounts differ and the payment is
 * refused with the usual "total does not match" message; the cashier opens the window again.
 */
public final class AutoPromotionService {

    /** How old the moment a payment window was opened at may be when the payment is confirmed (a window left open longer is refused). */
    static final java.time.Duration MAX_WINDOW_AGE = java.time.Duration.ofMinutes(15);
    /** How far ahead of the clock that moment may be (tills read the same clock; this only allows for a rounding). */
    static final java.time.Duration MAX_AHEAD = java.time.Duration.ofMinutes(1);
    /** How long the cart's live preview may use the promotions it read last (the payment itself always reads them fresh). */
    private static final long PREVIEW_CACHE_NANOS = 20_000_000_000L;

    private static List<Promotion> cachedPromotions;
    private static final Map<Integer, Integer> CACHED_CATEGORIES = new java.util.HashMap<>();
    private static final Set<Integer> CACHED_PRODUCTS = new java.util.HashSet<>();
    private static long cachedAt;

    private AutoPromotionService() {
    }

    /**
     * The moment a bill is priced at (TODO 4.55). The payment window records when it was opened and the payment carries it, so the
     * cashier pays what the window showed even if a happy hour ended while the customer was counting coins. It is never trusted
     * blindly: it must not be in the future and must not be older than {@link #MAX_WINDOW_AGE}; otherwise the payment is refused and
     * the window has to be opened again. With none given (an older caller) the bill is priced now.
     */
    public static LocalDateTime pricingInstant(LocalDateTime claimed, LocalDateTime now) {
        if (claimed == null) {
            return now;
        }
        if (claimed.isAfter(now.plus(MAX_AHEAD)) || claimed.isBefore(now.minus(MAX_WINDOW_AGE))) {
            throw new com.coffeeshop.coffeeshopmanagement.dao.DataAccessException(I18n.t("order.promo.stale", MAX_WINDOW_AGE.toMinutes()));
        }
        return claimed;
    }

    /** Forget what the cart's preview remembers (after an administrator changed a promotion, so this till shows it at once). */
    public static synchronized void forgetPreviewCache() {
        cachedPromotions = null;
        CACHED_CATEGORIES.clear();
        CACHED_PRODUCTS.clear();
    }

    /**
     * What the cart shows while the cashier is still ordering: the same answer the payment window will give, from promotions kept for
     * a few seconds so a click does not ask the database. It is only a preview: the payment window and the checkout read fresh.
     */
    public static synchronized Result preview(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Result.NONE;
        }
        long now = System.nanoTime();
        if (cachedPromotions == null || now - cachedAt > PREVIEW_CACHE_NANOS) {
            forgetPreviewCache();
            cachedPromotions = new PromotionDAO().findActive();
            cachedAt = now;
        }
        if (cachedPromotions.isEmpty()) {
            return Result.NONE;
        }
        Set<Integer> missing = new LinkedHashSet<>();
        for (OrderItem item : items) {
            Integer product = productOf(item);
            if (product != null && !CACHED_PRODUCTS.contains(product)) missing.add(product);
        }
        if (!missing.isEmpty()) {
            CACHED_CATEGORIES.putAll(new PromotionDAO().categoriesOf(missing));
            CACHED_PRODUCTS.addAll(missing);
        }
        return evaluate(items, cachedPromotions, CACHED_CATEGORIES, AppClock.now());
    }

    /** What a bill gets: the answer per line (same order as the bill), the total saving and the names of the promotions used. */
    public record Result(List<Given> lines, BigDecimal value, List<String> names) {
        public static final Result NONE = new Result(List.of(), BigDecimal.ZERO, List.of());

        public boolean isNone() {
            return value.signum() == 0;
        }

        /** Puts each line's promotion name and saving on the bill's lines (and clears what an earlier bill left there). */
        public void writeOnto(List<OrderItem> items) {
            for (int i = 0; i < items.size(); i++) {
                Given given = i < lines.size() ? lines.get(i) : null;
                if (given != null && given.saving() > 0) {
                    items.get(i).setPromoName(given.promotionName());
                    items.get(i).setPromoValue(Money.fromDong(given.saving()));
                } else {
                    items.get(i).setPromoName(null);
                    items.get(i).setPromoValue(null);
                }
            }
        }
    }

    /** What the bill gets now, from the shop's promotions in the database. */
    public static Result evaluate(List<OrderItem> items) {
        return evaluate(items, AppClock.now());
    }

    /** What the bill gets at {@code at}. Nothing is read when the bill has no lines. */
    public static Result evaluate(List<OrderItem> items, LocalDateTime at) {
        if (items == null || items.isEmpty()) {
            return Result.NONE;
        }
        PromotionDAO dao = new PromotionDAO();
        List<Promotion> promotions = dao.findActive();
        if (promotions.isEmpty()) {
            return Result.NONE;
        }
        Set<Integer> productIds = new LinkedHashSet<>();
        for (OrderItem item : items) {
            if (productOf(item) != null) productIds.add(productOf(item));
        }
        return evaluate(items, promotions, dao.categoriesOf(productIds), at);
    }

    /** The same with the promotions and the categories given: no database, so it is tested directly. */
    static Result evaluate(List<OrderItem> items, List<Promotion> promotions, Map<Integer, Integer> categoryOfProduct, LocalDateTime at) {
        List<Line> lines = new ArrayList<>();
        for (OrderItem item : items) {
            Integer product = productOf(item);
            long unit = item.getUnitPrice() == null ? 0 : Money.toDong(item.getUnitPrice());
            lines.add(new Line(product, product == null ? null : categoryOfProduct.get(product), unit, item.getQuantity()));
        }
        Applied applied = AutoPromotionRules.apply(lines, promotions, at);
        if (applied.isNone()) {
            return new Result(applied.lines(), BigDecimal.ZERO, List.of());
        }
        Set<String> names = new LinkedHashSet<>();
        for (Given given : applied.lines()) {
            if (given.saving() > 0) names.add(given.promotionName());
        }
        return new Result(applied.lines(), Money.fromDong(applied.total()), new ArrayList<>(names));
    }

    /** The line's product, or null for a line whose product was deleted (it keeps its name and price but can have no promotion). */
    private static Integer productOf(OrderItem item) {
        Integer id = item.getProductId();
        return id != null && id > 0 ? id : null;
    }

    /** The discount reason with the promotions' names added, the way a voucher and points add theirs: {@code "reason · Khuyến mãi: A, B"}. */
    public static String reasonWithPromotions(String reason, List<String> names) {
        if (names == null || names.isEmpty()) return reason;
        String note = I18n.t("order.discount.reason.promo", String.join(", ", names));
        return reason == null || reason.isBlank() ? note : reason + " · " + note;
    }

    // ---- how a promotion is told to the administrator

    /** What a promotion does, in words: "10% off", "39.000đ each", "Buy 2 get 1 free", "Combo 70.000đ". */
    public static String worth(Promotion promotion) {
        if (promotion.kind() == null) return "";
        return switch (promotion.kind()) {
            case PERCENT -> I18n.t("promo.worth.PERCENT", String.valueOf(promotion.percent()));
            case FIXED_PRICE -> I18n.t("promo.worth.FIXED_PRICE", com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil.format(Money.fromDong(promotion.fixedPrice())));
            case BUY_GET -> I18n.t("promo.worth.BUY_GET", String.valueOf(promotion.buy()), String.valueOf(promotion.free()));
            case COMBO -> I18n.t("promo.worth.COMBO", com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil.format(Money.fromDong(promotion.fixedPrice())));
        };
    }

    /** When a promotion is on, in words: the days (or "every day"), the hours (or "all day") and the dates when it has them. */
    public static String when(Promotion promotion) {
        StringBuilder text = new StringBuilder();
        if (promotion.days().isEmpty() || promotion.days().size() == 7) {
            text.append(I18n.t("promo.when.everyday"));
        } else {
            List<String> names = new ArrayList<>();
            for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
                if (promotion.days().contains(day)) names.add(dayName(day));
            }
            text.append(String.join(", ", names));
        }
        text.append(", ");
        text.append(promotion.from() == null || promotion.to() == null ? I18n.t("promo.when.allday") : promotion.from() + "–" + promotion.to());
        if (promotion.validFrom() != null || promotion.validTo() != null) {
            text.append(", ").append(promotion.validFrom() == null ? "…" : promotion.validFrom().toString()).append(" – ")
                    .append(promotion.validTo() == null ? "…" : promotion.validTo().toString());
        }
        return text.toString();
    }

    /** The name of a day for the lists (explicit keys, so the key checks of the tests can see them). */
    public static String dayName(java.time.DayOfWeek day) {
        return switch (day) {
            case MONDAY -> I18n.t("promo.day.1");
            case TUESDAY -> I18n.t("promo.day.2");
            case WEDNESDAY -> I18n.t("promo.day.3");
            case THURSDAY -> I18n.t("promo.day.4");
            case FRIDAY -> I18n.t("promo.day.5");
            case SATURDAY -> I18n.t("promo.day.6");
            case SUNDAY -> I18n.t("promo.day.7");
        };
    }

    /** The name of a kind of promotion for the lists. */
    public static String kindName(AutoPromotionRules.Kind kind) {
        return switch (kind) {
            case PERCENT -> I18n.t("promo.kind.PERCENT");
            case FIXED_PRICE -> I18n.t("promo.kind.FIXED_PRICE");
            case BUY_GET -> I18n.t("promo.kind.BUY_GET");
            case COMBO -> I18n.t("promo.kind.COMBO");
        };
    }

    /** The plain-language text for a code that {@link AutoPromotionRules#problemWith} returned. */
    public static String problemText(String code) {
        return switch (code) {
            case "name" -> I18n.t("promo.val.name");
            case "kind" -> I18n.t("promo.val.kind");
            case "targets" -> I18n.t("promo.val.targets");
            case "hours" -> I18n.t("promo.val.hours");
            case "dates" -> I18n.t("promo.val.dates");
            case "percent" -> I18n.t("promo.val.percent");
            case "price" -> I18n.t("promo.val.price");
            case "buyget" -> I18n.t("promo.val.buyget");
            case "combo" -> I18n.t("promo.val.combo");
            default -> I18n.t("promo.val.gone");
        };
    }
}
