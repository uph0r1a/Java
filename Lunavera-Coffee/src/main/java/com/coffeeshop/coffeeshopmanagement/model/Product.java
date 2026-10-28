package com.coffeeshop.coffeeshopmanagement.model;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.math.BigDecimal;

public class Product {
    private int id;
    private String name;
    private Integer categoryId;
    private String categoryName; // populated by joins for display; not a DB column here
    private BigDecimal price;
    private BigDecimal cost;
    private int stock;
    /** The stock number as it was when this object was read from the database; null for a product not read yet (TODO 3.2). */
    private Integer loadedStock;
    private String description;
    private String imagePath;
    private boolean active;
    /** Position among the products of its category on the sales screen, smallest first; equal numbers keep the order of creation (TODO 5.31). */
    private int sortOrder;
    /** The product's own reorder level (TODO 4.19); null = the shop default, see {@code StockLevel}. */
    private Integer reorderLevel;
    /** The barcode / product code in capitals (TODO 4.42); null = none. Unique among products. */
    private String code;

    public Product() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public Integer getLoadedStock() { return loadedStock; }
    public void setLoadedStock(Integer loadedStock) { this.loadedStock = loadedStock; }
    /** True when this object was read from the database and its stock number was not changed since. */
    public boolean isStockUntouchedSinceLoad() { return loadedStock != null && loadedStock == stock; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Integer getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(Integer reorderLevel) { this.reorderLevel = reorderLevel; }
    /** True when the stock is at or below this product's reorder level (its own, or the shop default). */
    public boolean isLowStock() { return com.coffeeshop.coffeeshopmanagement.service.StockLevel.isLow(stock, reorderLevel); }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        // Used by ChoiceDialog/ComboBox cells elsewhere in the UI (product picker in the
        // POS screen, category picker in the add/edit product dialog). Kept dependency-free
        // (no CurrencyUtil) since models shouldn't reach into the util/display layer.
        return price != null ? name + " - " + price.toPlainString() + "đ" : name;
    }
}
