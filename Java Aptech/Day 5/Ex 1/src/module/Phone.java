package module;

public class Phone extends ElectronicDevice implements Discountable {
    private double batteryLife;
    private boolean isSupport5G;

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
        System.out.println("Phone");
        System.out.println("ID: " + getID());
        System.out.println("Name: " + getName());
        System.out.println("Brand: " + getBrand());
        System.out.println("Price: " + getPrice());
        System.out.println("Battery life: " + batteryLife);
        System.out.println("Support 5G: " + (isSupport5G ? "Yes" : "No"));
    }

    public double getBatteryLife() {
        return batteryLife;
    }

    public boolean isSupport5G() {
        return isSupport5G;
    }
}