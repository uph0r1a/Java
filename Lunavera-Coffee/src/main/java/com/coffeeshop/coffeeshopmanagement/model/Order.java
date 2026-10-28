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

    public Order() {
    }

    public BigDecimal getCashPart() { return cashPart; }
    public void setCashPart(BigDecimal cashPart) { this.cashPart = cashPart; }
    public boolean isSplitPayment() { return cashPart != null; }

    public Integer getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(Integer pointsEarned) { this.pointsEarned = pointsEarned; }
    public Integer getPointsRedeemed() { return pointsRedeemed; }
    public void setPointsRedeemed(Integer pointsRedeemed) { this.pointsRedeemed = pointsRedeemed; }
    public BigDecimal getPointsValue() { return pointsValue; }
    public void setPointsValue(BigDecimal pointsValue) { this.pointsValue = pointsValue; }

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
