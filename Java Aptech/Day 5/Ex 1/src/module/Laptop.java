package module;

public class Laptop extends ElectronicDevice {
    private double ram;
    private double screenSize;

    public Laptop(String ID, String name, String brand, int price, double ram, double screenSize) {
        super(ID, name, brand, price);
        this.ram = ram;
        this.screenSize = screenSize;
    }

    public double getRam() {
        return ram;
    }

    public void setRam(double ram) {
        this.ram = ram;
    }

    public double getScreenSize() {
        return screenSize;
    }

    public void setScreenSize(double screenSize) {
        this.screenSize = screenSize;
    }

}
