package module;

public class Car extends Vehicle {
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
}
