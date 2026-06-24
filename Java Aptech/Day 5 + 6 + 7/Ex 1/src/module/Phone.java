package module;

public class Phone extends ElectronicDevice implements Discountable {
    private final double batteryLife;
    private final boolean isSupport5G;

    public Phone(String ID, String name, String brand, int price,
            double batteryLife, boolean isSupport5G) {
        super(ID, name, brand, price);
        this.batteryLife = batteryLife;
        this.isSupport5G = isSupport5G;
    }

    @Override
    public double calculateDiscountPrice() {
        return getPrice() * 0.9;
    }

    @Override
    public void displayInfo() {
        System.out.println("Laptop\nID: " + getID() + "\nName: " + getName() + "\nBrand: " + getBrand() + "\nPrice: "
                + getPrice() + "\nBattery life: " + batteryLife + "\nSupport 5G: " + (isSupport5G ? "Yes" : "No"));
    }

    public double getBatteryLife() {
        return batteryLife;
    }

    public boolean isSupport5G() {
        return isSupport5G;
    }
}