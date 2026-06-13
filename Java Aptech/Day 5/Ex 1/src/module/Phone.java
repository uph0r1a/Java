package module;

public class Phone extends ElectronicDevice {
    private double batteryLife;
    private boolean isSupport5G;

    public Phone(String ID, String name, String brand, int price, double batteryLife, boolean isSupport5G) {
        super(ID, name, brand, price);
        this.batteryLife = batteryLife;
        this.isSupport5G = isSupport5G;
    }

    public double getBatteryLife() {
        return batteryLife;
    }

    public void setBatteryLife(double batteryLife) {
        this.batteryLife = batteryLife;
    }

    public boolean isSupport5G() {
        return isSupport5G;
    }

    public void setSupport5G(boolean isSupport5G) {
        this.isSupport5G = isSupport5G;
    }
}
