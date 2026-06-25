package module;

public class Motorcycle extends Vehicle implements Registrable {
    private double engineCapacity;
    private boolean ABSSupported;

    public Motorcycle(String iD, String name, String manufacturer, int value, double engineCapacity,
            boolean ABSSupported, String insuranceProvider, double coverageAmount) {
        super(iD, name, manufacturer, value, insuranceProvider, coverageAmount);
        this.engineCapacity = engineCapacity;
        this.ABSSupported = ABSSupported;
        VehicleStatistics.addMotorcycle(value);
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

    @Override
    public double calculateAnnualTax() {
        if (getEngineCapacity() < 150) {
            return getValue() * 0.02;
        } else if (getEngineCapacity() >= 150) {
            return getValue() * 0.04;
        }
        throw new UnsupportedOperationException("Unimplemented method 'calculateAnnualTax'");
    }

    @Override
    public String getRegistrationStatus() {
        if (ABSSupported) {
            return "Safety Certified";
        } else {
            return "Basic Certified";
        }
    }
}