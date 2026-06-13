package module;

public class Motorcycle extends Vehicle {
    private double engineCapacity;
    private boolean ABSSupported;

    public Motorcycle(String iD, String name, String manufacturer, int value, double engineCapacity,
            boolean ABSSupported) {
        super(iD, name, manufacturer, value);
        this.engineCapacity = engineCapacity;
        this.ABSSupported = ABSSupported;
    }

    public double getEngineCapacity() {
        return engineCapacity;
    }

    public void setEngineCapacity(double engineCapacity) {
        this.engineCapacity = engineCapacity;
    }

    public boolean isABSSupported() {
        return ABSSupported;
    }

    public void setABSSupported(boolean ABSSupported) {
        this.ABSSupported = ABSSupported;
    }

    @Override
    public String display() {
        return super.display() + "\nEngine capacity: " + engineCapacity + " cc\nABS supported: "
                + (ABSSupported ? "Yes" : "No");
    }

}
