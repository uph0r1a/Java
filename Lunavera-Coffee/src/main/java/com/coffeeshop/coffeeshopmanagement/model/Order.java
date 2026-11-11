package com.coffeeshop.coffeeshopmanagement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Order {
    private int id;
    private LocalDateTime orderDate;
    private Integer employeeId;
    private Integer customerId;
    private OrderStatus status;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal total;
    private PaymentMethod paymentMethod;
    private LocalDateTime paidAt;
    private Integer tableNumber;
    /** Why a discount was given; null when there was none (TODO 2.3). */
    private String discountReason;
    /** The loyalty points this order gave its customer; null when it was paid before that was recorded (TODO 3.25). */
    private Integer pointsEarned;
    /** Points the customer spent on this order and what they were worth (TODO 4.16); null when none were spent. Their value is part of {@link #discount}. */
    private Integer pointsRedeemed;
    private BigDecimal pointsValue;
    private String voucherCode;
    private BigDecimal voucherValue;
    /** What the automatic promotions took off (TODO 4.55); also inside {@code discount}. Null = none. */
    private BigDecimal promoValue;
    /** What the customer handed over and what went back (cash payments only; null otherwise, and for old orders) (TODO 3.17). */
    private BigDecimal cashReceived;
    private BigDecimal changeGiven;
    /** The bank's reference of a transfer, as the cashier typed it; null when none was typed (TODO 4.31). */
    private String transferRef;

    /**
     * The part of the total paid in cash when the order was paid with two methods (TODO 4.32), else null. The rest was paid with
     * {@link #getPaymentMethod()}, which is never cash for such an order. Read from {@code order_payments}, not stored in this row.
     */
    private BigDecimal cashPart;
    /** Not saved with the order: asks the payment to record that the customer's birthday discount is given today (TODO 3.66). */
    private boolean birthdayDiscount;
    /** The take-away number of the day, 1, 2, 3 ... (TODO 4.25, D3); null for an order at a table. */
    private Integer queueNo;
    /** The revision of the open order as this screen last saw or saved it (TODO 2.91); see {@code OrderDAO.updateIfStillOpen}. */
    private int rev;


    /** Where the order came from (TODO 4.48); null = not recorded (an old order): see {@link #channel()}. */
    private OrderChannel channel;

    public Order() {
    }

    /** The gift cards this order paid with, for the receipt (TODO 5.51); not saved with the order, read when a receipt is shown. */
    private java.util.List<GiftCardUse> giftCardUses = java.util.List.of();
    public java.util.List<GiftCardUse> getGiftCardUses() { return giftCardUses; }
    public void setGiftCardUses(java.util.List<GiftCardUse> uses) { this.giftCardUses = uses == null ? java.util.List.of() : java.util.List.copyOf(uses); }
    /** True when the order, or one line of a split payment, was paid with a gift card. */
    public boolean paidWithGiftCard() {
        if (paymentMethod == PaymentMethod.GIFT_CARD) return true;
        for (PaymentLine line : getPaymentLines()) if (line.method() == PaymentMethod.GIFT_CARD) return true;
        return false;
    }

    /** How many times the receipt was printed (TODO 3.72); 0 = never. The next print is a copy when this is 1 or more. */
    private int printCount;

    public int getPrintCount() { return printCount; }
    public void setPrintCount(int printCount) { this.printCount = Math.max(0, printCount); }
    /** The number of the print that comes next, when it is a copy (2, 3, ...), else 0 (the first print, no mark). */
    public int nextCopyNumber() { return printCount >= 1 ? printCount + 1 : 0; }

    public OrderChannel getChannel() { return channel; }
    public void setChannel(OrderChannel channel) { this.channel = channel; }
    /** The channel the order really has: the recorded one, else worked out from the table (see {@link OrderChannel#of}). */
    public OrderChannel channel() { return OrderChannel.of(channel, tableNumber); }

    public Integer getQueueNo() { return queueNo; }
    public void setQueueNo(Integer queueNo) { this.queueNo = queueNo; }
    public int getRev() { return rev; }
    public void setRev(int rev) { this.rev = rev; }
    public boolean isBirthdayDiscount() { return birthdayDiscount; }
    public void setBirthdayDiscount(boolean birthdayDiscount) { this.birthdayDiscount = birthdayDiscount; }

    public BigDecimal getCashPart() { return cashPart; }
    public void setCashPart(BigDecimal cashPart) { this.cashPart = cashPart; }
    /** What each method paid, for an order paid with two or more methods (TODO 4.32, D4: any number); empty for one method. */
    private java.util.List<PaymentLine> paymentLines = java.util.List.of();
    public void setPaymentLines(java.util.List<PaymentLine> lines) { this.paymentLines = lines == null ? java.util.List.of() : java.util.List.copyOf(lines); }

    /**
     * The lines of the payment: the ones set, or for an order that only has a {@link #getCashPart() cash part} (the first form of 4.32:
     * cash plus the order's method) the two lines worked out from it; empty for a payment with one method.
     */
    public java.util.List<PaymentLine> getPaymentLines() {
        if (!paymentLines.isEmpty()) return paymentLines;
        if (cashPart != null && paymentMethod != null && total != null) {
            return java.util.List.of(new PaymentLine(PaymentMethod.CASH, cashPart), new PaymentLine(paymentMethod, total.subtract(cashPart)));
        }
        return java.util.List.of();
    }

    public boolean isSplitPayment() { return !getPaymentLines().isEmpty(); }

    public Integer getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(Integer pointsEarned) { this.pointsEarned = pointsEarned; }
    public Integer getPointsRedeemed() { return pointsRedeemed; }
    public void setPointsRedeemed(Integer pointsRedeemed) { this.pointsRedeemed = pointsRedeemed; }
    public BigDecimal getPointsValue() { return pointsValue; }
    public void setPointsValue(BigDecimal pointsValue) { this.pointsValue = pointsValue; }
    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }
    public BigDecimal getVoucherValue() { return voucherValue; }
    public void setVoucherValue(BigDecimal voucherValue) { this.voucherValue = voucherValue; }
    public BigDecimal getPromoValue() { return promoValue; }
    public void setPromoValue(BigDecimal promoValue) { this.promoValue = promoValue; }

    public BigDecimal getCashReceived() { return cashReceived; }
    public void setCashReceived(BigDecimal cashReceived) { this.cashReceived = cashReceived; }

    public BigDecimal getChangeGiven() { return changeGiven; }
    public void setChangeGiven(BigDecimal changeGiven) { this.changeGiven = changeGiven; }

    public String getTransferRef() { return transferRef; }
    public void setTransferRef(String transferRef) { this.transferRef = transferRef; }

    public String getDiscountReason() { return discountReason; }
    public void setDiscountReason(String discountReason) { this.discountReason = discountReason; }

    public Integer getTableNumber() { return tableNumber; }
    public void setTableNumber(Integer tableNumber) { this.tableNumber = tableNumber; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public LocalDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }

    public Integer getEmployeeId() { return employeeId; }
    public void setEmployeeId(Integer employeeId) { this.employeeId = employeeId; }

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
