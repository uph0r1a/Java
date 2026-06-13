package module;

public class Vehicle {
    private String ID, name, manufacturer;
    private int value;

    public Vehicle(String ID, String name, String manufacturer, int value) {
        this.ID = ID;
        this.name = name;
        this.manufacturer = manufacturer;
        this.value = value;
    }

    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String display() {
        return "\nID: " + ID + "\nName: " + name + "\nManufacturer: " + manufacturer + "\nValue: " + value;
    }
}
