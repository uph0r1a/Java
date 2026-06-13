package module;

public class Phone {
    private String phoneID, phoneName, brand;
    private double price;
    private int stock;

    public Phone(String phoneID, String phoneName, String brand, double price, int stock) {
        this.phoneID = phoneID;
        this.phoneName = phoneName;
        this.brand = brand;
        this.price = price;
        this.stock = stock;
    }

    public void setPhoneID(String phoneID) {
        this.phoneID = phoneID;
    }

    public void setPhoneName(String phoneName) {
        this.phoneName = phoneName;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getPhoneID() {
        return phoneID;
    }

    public String getPhoneName() {
        return phoneName;
    }

    public String getBrand() {
        return brand;
    }

    public double getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

}
