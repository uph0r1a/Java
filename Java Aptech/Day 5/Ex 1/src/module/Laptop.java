package module;

public class Laptop extends ElectronicDevice implements Discountable {
    private double ram;
    private double screenSize;

    public Laptop(String ID, String name, String brand, int price,
            double ram, double screenSize) {
        super(ID, name, brand, price);
        this.ram = ram;
        this.screenSize = screenSize;
    }

    @Override
    public double calculateDiscountPrice() {
        return getPrice() * 0.85;
    }

    @Override
    public void displayInfo() {
        System.out.println("Laptop");
        System.out.println("ID: " + getID());
        System.out.println("Name: " + getName());
        System.out.println("Brand: " + getBrand());
        System.out.println("Price: " + getPrice());
        System.out.println("RAM: " + ram);
        System.out.println("Screen size: " + screenSize);
    }

    public double getRam() {
        return ram;
    }

    public double getScreenSize() {
        return screenSize;
    }
}