import java.util.UUID;

public class Ex8 {
    public static interface IVehicle {
        public void startEngine();

        public void stopEngine();

        public void brake();
    }

    public static class Vehicle implements IVehicle {
        private String id, manufacturer, color;
        private int yearOfManufacture;
        private double sellingPrice;

        public Vehicle(String manufacturer, String color, int yearOfManufacture, double sellingPrice) {
            this.id = UUID.randomUUID().toString();
            this.manufacturer = manufacturer;
            this.color = color;
            this.yearOfManufacture = yearOfManufacture;
            this.sellingPrice = sellingPrice;
        }

        public String getId() {
            return id;
        }

        public String getManufacturer() {
            return manufacturer;
        }

        public void setManufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public int getYearOfManufacture() {
            return yearOfManufacture;
        }

        public void setYearOfManufacture(int yearOfManufacture) {
            this.yearOfManufacture = yearOfManufacture;
        }

        public double getSellingPrice() {
            return sellingPrice;
        }

        public void setSellingPrice(double sellingPrice) {
            this.sellingPrice = sellingPrice;
        }

        @Override
        public void startEngine() {

        };

        @Override
        public void stopEngine() {
            System.out.println("Stopping engine");
        }

        @Override
        public void brake() {
            System.out.println("Braking");
        }
    }

    public static class Car extends Vehicle {
        private int numberOfSeats;
        private String engineType;

        public Car(String manufacturer, String color, int yearOfManufacture, double sellingPrice,
                int numberOfSeats, String engineType) {
            super(manufacturer, color, yearOfManufacture, sellingPrice);
            this.numberOfSeats = numberOfSeats;
            this.engineType = engineType;
        }

        public int getNumberOfSeats() {
            return numberOfSeats;
        }

        public void setNumberOfSeats(int numberOfSeats) {
            this.numberOfSeats = numberOfSeats;
        }

        public String getEngineType() {
            return engineType;
        }

        public void setEngineType(String engineType) {
            this.engineType = engineType;
        }

        @Override
        public void startEngine() {
            System.out.println("Starting car engine");
        }
    }

    public static class Motorcycle extends Vehicle {
        private int enginePower;

        public Motorcycle(String manufacturer, String color, int yearOfManufacture, double sellingPrice,
                int enginePower) {
            super(manufacturer, color, yearOfManufacture, sellingPrice);
            this.enginePower = enginePower;
        }

        public int getEnginePower() {
            return enginePower;
        }

        public void setEnginePower(int enginePower) {
            this.enginePower = enginePower;
        }

        @Override
        public void startEngine() {
            System.out.println("Starting motorcycle engine");
        }
    }

    public static class Truck extends Vehicle {
        private int loadCapacity;

        public Truck(String manufacturer, String color, int yearOfManufacture, double sellingPrice,
                int loadCapacity) {
            super(manufacturer, color, yearOfManufacture, sellingPrice);
            this.loadCapacity = loadCapacity;
        }

        public int getLoadCapacity() {
            return loadCapacity;
        }

        public void setLoadCapacity(int loadCapacity) {
            this.loadCapacity = loadCapacity;
        }

        @Override
        public void startEngine() {
            System.out.println("Starting truck engine");
        }

    }

    public static void main(String[] args) {

    }
}
