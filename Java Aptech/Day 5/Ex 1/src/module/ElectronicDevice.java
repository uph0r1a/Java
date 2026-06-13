package module;

public class ElectronicDevice {
    private String ID, name, brand;
    private int price;

    public ElectronicDevice(String ID, String name, String brand, int price) {
        this.ID = ID;
        this.name = name;
        this.brand = brand;
        this.price = price;
    }

    public String getID() {
        return ID;
    }

    public void setID(String iD) {
        ID = iD;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }
}
