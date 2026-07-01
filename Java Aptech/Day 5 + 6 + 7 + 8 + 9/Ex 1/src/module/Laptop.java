package module;

import java.time.LocalDate;

public class Laptop extends ElectronicDevice implements Discountable {
    private final double ram;
    private final double screenSize;

    public Laptop(String ID, String name, String brand, int price, double ram, double screenSize,
            LocalDate importDate) {
        super(ID, name, brand, price, importDate);
        this.ram = ram;
        this.screenSize = screenSize;
    }

    @Override
    public double calculateDiscountPrice() {
        return getPrice() * 0.85;
    }

    @Override
    public void displayInfo() {
        System.out.println("Laptop\nID: " + getID() + "\nName: " + getName() + "\nBrand: " + getBrand() + "\nPrice: "
                + getPrice() + "\nRAM: " + ram + "\nScreen size: " + screenSize + "\nImport date: " + getImportDate());
    }

    public double getRam() {
        return ram;
    }

    public double getScreenSize() {
        return screenSize;
    }
}