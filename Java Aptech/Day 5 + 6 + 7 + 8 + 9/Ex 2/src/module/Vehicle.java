package module;

import java.time.LocalDate;

public abstract class Vehicle implements Comparable<Vehicle> {
    private String ID, name, manufacturer;
    private int value;
    private VehicleInsurance insurance;
    private LocalDate registrationDate;

    public Vehicle(String ID, String name, String manufacturer, int value,
            String insuranceProvider, double coverageAmount, LocalDate registrationDate) {
        this.ID = ID;
        this.name = name;
        this.manufacturer = manufacturer;
        this.value = value;
        this.insurance = new VehicleInsurance(insuranceProvider, coverageAmount);
        this.registrationDate = registrationDate;
    }

    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public VehicleInsurance getInsurance() {
        return insurance;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
    }

    public String display() {
        return "\nID: " + ID + "\nName: " + name + "\nManufacturer: " + manufacturer + "\nValue: " + value
                + "\nInsurance Provider: " + insurance.getInsuranceProvider()
                + "\nCoverage Amount: " + insurance.getCoverageAmount()
                + "\nRegistration Date: " + registrationDate;
    }

    @Override
    public int compareTo(Vehicle other) {
        return Integer.compare(this.value, other.value);
    }

    public abstract double calculateAnnualTax();

    public class VehicleInsurance {
        private String insuranceProvider;
        private double coverageAmount;

        public VehicleInsurance(String insuranceProvider, double coverageAmount) {
            this.insuranceProvider = insuranceProvider;
            this.coverageAmount = coverageAmount;
        }

        public String getInsuranceProvider() {
            return insuranceProvider;
        }

        public double getCoverageAmount() {
            return coverageAmount;
        }
    }

    public static class VehicleStatistics {
        private static int totalVehicles = 0;
        private static int totalCars = 0;
        private static int totalMotorcycles = 0;
        private static double totalVehicleValue = 0;

        public static int getTotalVehicles() {
            return totalVehicles;
        }

        public static int getTotalCars() {
            return totalCars;
        }

        public static int getTotalMotorcycles() {
            return totalMotorcycles;
        }

        public static double getTotalVehicleValue() {
            return totalVehicleValue;
        }

        public static void addCar(int value) {
            totalVehicles++;
            totalCars++;
            totalVehicleValue += value;
        }

        public static void addMotorcycle(int value) {
            totalVehicles++;
            totalMotorcycles++;
            totalVehicleValue += value;
        }
    }
}