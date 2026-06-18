package module;

public abstract class ElectronicDevice {
    private String ID, name, brand;
    private int price;

    public ElectronicDevice(String ID, String name, String brand, int price) {
        this.ID = ID;
        this.name = name;
        this.brand = brand;
        this.price = price;
    }

    public abstract void displayInfo();

    public String getID() {
        return ID;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public int getPrice() {
        return price;
    }
}