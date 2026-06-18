package module;

public class Car extends Vehicle implements Registrable {
    public enum FUEL_TYPE {
        Gasoline,
        Diesel,
        Electric
    }

    private int numberOfSeats;
    private FUEL_TYPE fuelType;

    public Car(String iD, String name, String manufacturer, int value, int numberOfSeats, FUEL_TYPE fuelType) {
        super(iD, name, manufacturer, value);
        this.numberOfSeats = numberOfSeats;
        this.fuelType = fuelType;
    }

    public int getNumberOfSeats() {
        return numberOfSeats;
    }

    public void setNumberOfSeats(int numberOfSeats) {
        this.numberOfSeats = numberOfSeats;
    }

    public FUEL_TYPE getFuelType() {
        return fuelType;
    }

    public void setFuelType(FUEL_TYPE fuelType) {
        this.fuelType = fuelType;
    }

    @Override
    public String display() {
        return super.display() + "\nNumber of seat: " + numberOfSeats + "\nFuel type: " + fuelType.toString();
    }

    @Override
    public double calculateAnnualTax() {
        String type = getFuelType().toString().toLowerCase();
        if (type.equals("gasoline")) {
            return getValue() * 0.05;
        } else if (type.equals("diesel")) {
            return getValue() * 0.06;
        } else if (type.equals("electric")) {
            return getValue() * 0.03;
        }
        throw new UnsupportedOperationException("Unimplemented method 'calculateAnnualTax'");
    }

    @Override
    public String getRegistrationStatus() {
        String type = getFuelType().toString().toLowerCase();
        if (type.equals("electric")) {
            return "Green Vehicle";
        } else if (type.equals("diesel") || type.equals("gasoline")) {
            return "Standard Vehicle";
        }
        throw new UnsupportedOperationException("Unimplemented method 'getRegistrationStatus'");
    }
}
