package module;

import java.time.LocalDate;

public class Car extends Vehicle implements Registrable {
    public enum FUEL_TYPE {
        Gasoline,
        Diesel,
        Electric
    }

    private int numberOfSeats;
    private FUEL_TYPE fuelType;

    public Car(String iD, String name, String manufacturer, int value, int numberOfSeats, FUEL_TYPE fuelType,
            String insuranceProvider, double coverageAmount, LocalDate registrationDate) {
        super(iD, name, manufacturer, value, insuranceProvider, coverageAmount, registrationDate);
        this.numberOfSeats = numberOfSeats;
        this.fuelType = fuelType;
        VehicleStatistics.addCar(value);
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
        return switch (type) {
            case "gasoline" -> getValue() * 0.05;
            case "diesel" -> getValue() * 0.06;
            case "electric" -> getValue() * 0.03;
            default -> throw new UnsupportedOperationException("Unimplemented method 'calculateAnnualTax'");
        };
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