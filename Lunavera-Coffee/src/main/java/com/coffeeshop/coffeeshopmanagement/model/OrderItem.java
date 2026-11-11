package com.coffeeshop.coffeeshopmanagement.model;

import java.math.BigDecimal;

/**
 * The product name and unit price are copied ("snapshotted") onto the order item at the
 * time of sale, so that editing or deactivating a product later never changes the
 * historical record of what a past order actually contained and charged.
 */
public class OrderItem {
    private int id;
    private Integer productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    /** What the customer asked for on this line ("less sugar"), see {@code OrderNote}; null = nothing (TODO 4.9). */
    private String note;
    /** The size chosen at the till, as it was named then (TODO 4.47); null = the product has no size or none was chosen. */
    private String sizeName;
    /** How much of the recipe one piece of the chosen size uses, 100 = normal; null = no size, read as 100 (TODO 4.47). */
    private Integer sizePercent;
    /** The chosen extras with their prices at the time, in the form {@code LineOptions} writes ("Extra shot=10000;Cream=5000"); null = none. */
    private String extras;
    /** What the size and the extras add to the cost of one piece (TODO 4.47); null = nothing. */
    private java.math.BigDecimal optionCost;
    /**
     * The automatic promotion this line got and what it took off the line (TODO 4.55), set when the order is paid; null when none.
     * {@code lineTotal} stays unit price x quantity: the saving is part of the order's discount, and the receipt prints it under the line.
     */
    private String promoName;
    private BigDecimal promoValue;

    public OrderItem() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }


    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getSizeName() { return sizeName; }
    public void setSizeName(String sizeName) { this.sizeName = sizeName; }

    public Integer getSizePercent() { return sizePercent; }
    public void setSizePercent(Integer sizePercent) { this.sizePercent = sizePercent; }

    public String getExtras() { return extras; }
    public void setExtras(String extras) { this.extras = extras; }

    public java.math.BigDecimal getOptionCost() { return optionCost; }
    public void setOptionCost(java.math.BigDecimal optionCost) { this.optionCost = optionCost; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }

    public String getPromoName() { return promoName; }
    public void setPromoName(String promoName) { this.promoName = promoName; }

    public BigDecimal getPromoValue() { return promoValue; }
    public void setPromoValue(BigDecimal promoValue) { this.promoValue = promoValue; }
}
