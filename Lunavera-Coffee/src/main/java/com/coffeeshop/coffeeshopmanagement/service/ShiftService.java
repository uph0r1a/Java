package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.GiftCardDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO;
import com.coffeeshop.coffeeshopmanagement.model.ShiftClose;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * What the shift-close box reads and the one thing it writes (TODO 6.21): where the shift began, its totals, the cash taken out of the
 * drawer, the orders still open, what is owed on gift cards, the split by payment method, and the close itself. How a shift is counted
 * and what a close stores is {@link ShiftDAO}'s; the box ({@code ShiftCloseDialog}) used to build three DAOs for this.
 */
public class ShiftService {

    private final ShiftDAO shifts;
    private final GiftCardDAO giftCards;
    private final ReportDAO reports;

    public ShiftService() {
        this(new ShiftDAO(), new GiftCardDAO(), new ReportDAO());
    }

    ShiftService(ShiftDAO shifts, GiftCardDAO giftCards, ReportDAO reports) {
        this.shifts = shifts;
        this.giftCards = giftCards;
        this.reports = reports;
    }

    public LocalDateTime currentShiftStart() {
        return shifts.currentShiftStart();
    }

    public ShiftDAO.Summary summarize(LocalDateTime from, LocalDateTime to) {
        return shifts.summarize(from, to);
    }

    public Optional<BigDecimal> lastCountedCash() {
        return shifts.lastCountedCash();
    }

    /** Cash already taken out of the drawer between {@code from} and {@code to}. */
    public BigDecimal cashOut(LocalDateTime from, LocalDateTime to) {
        return shifts.cashOut(from, to);
    }

    public List<ShiftOpenOrders.Open> openOrders() {
        return shifts.openOrders();
    }

    /** What the shop still owes on gift cards. */
    public GiftCardDAO.OpenBalance giftCardsOpenBalance() {
        return giftCards.openBalance();
    }

    /** Cash, card and transfer of the window, to match the bank statement. */
    public List<ReportDAO.PaymentSplit> paymentSplit(LocalDateTime from, LocalDateTime to) {
        return reports.paymentSplit(from, to);
    }

    public ShiftClose close(LocalDateTime from, BigDecimal openingCash, BigDecimal countedCash, String note) {
        return shifts.close(from, openingCash, countedCash, note);
    }

    public ShiftSheet.Extras sheetExtras(LocalDateTime from, LocalDateTime to) {
        return shifts.sheetExtras(from, to);
    }

    public Optional<LocalDateTime> lastCloseEnd() {
        return shifts.lastCloseEnd();
    }

    public List<ShiftClose> findRecent(int limit) {
        return shifts.findRecent(limit);
    }
}
