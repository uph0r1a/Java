import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Ex8 {
    public static abstract class ElectronicDevice {
        private String brand;
        private String model;
        private double price;

        public ElectronicDevice(String brand, String model, double price) {
            this.brand = (brand != null && !brand.isBlank()) ? brand : "Unknown";
            this.model = (model != null && !model.isBlank()) ? model : "Unknown";
            this.price = price > 0 ? price : 0;
        }

        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = (brand != null && !brand.isBlank()) ? brand : "Unknown";
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = (model != null && !model.isBlank()) ? model : "Unknown";
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price > 0 ? price : 0;
        }

        public abstract double calculatePowerConsumption();

        public abstract String getDeviceType();

        public void displayInfo() {
            System.out.println("Brand: " + brand + "\nModel: " + model + "\nPrice: " + price
                    + "\nDevice Type: " + getDeviceType() + "\nPower Consumption: " + calculatePowerConsumption()
                    + " W\n-----------------------------");
        }
    }

    public static class SmartPhone extends ElectronicDevice {
        private double screenSize;
        private int batteryCapacity;

        public SmartPhone(String brand, String model, double price, double screenSize, int batteryCapacity) {
            super(brand, model, price);
            this.screenSize = screenSize > 0 ? screenSize : 0;
            this.batteryCapacity = batteryCapacity > 0 ? batteryCapacity : 0;
        }

        @Override
        public double calculatePowerConsumption() {
            return batteryCapacity * 0.05;
        }

        @Override
        public String getDeviceType() {
            return "Smartphone";
        }
    }

    public static class Laptop extends ElectronicDevice {
        private int ram;
        private double screenSize;

        public Laptop(String brand, String model, double price, int ram, double screenSize) {
            super(brand, model, price);
            this.ram = ram > 0 ? ram : 0;
            this.screenSize = screenSize > 0 ? screenSize : 0;
        }

        @Override
        public double calculatePowerConsumption() {
            return ram * 8.5;
        }

        @Override
        public String getDeviceType() {
            return "Laptop";
        }
    }

    public static class Tablet extends ElectronicDevice {
        private double screenSize;
        private int batteryCapacity;

        public Tablet(String brand, String model, double price, double screenSize, int batteryCapacity) {
            super(brand, model, price);
            this.screenSize = screenSize > 0 ? screenSize : 0;
            this.batteryCapacity = batteryCapacity > 0 ? batteryCapacity : 0;
        }

        @Override
        public double calculatePowerConsumption() {
            return batteryCapacity * 0.04;
        }

        @Override
        public String getDeviceType() {
            return "Tablet";
        }
    }

    public static void main(String[] args) {
        List<ElectronicDevice> devices = new ArrayList<>();
        Collections.addAll(devices,
                new SmartPhone("Samsung", "S23", 25000, 6.1, 4500),
                new SmartPhone("Apple", "iPhone 15", 32000, 6.1, 4200),
                new Laptop("Dell", "XPS 15", 45000, 16, 15.6),
                new Tablet("Samsung", "Tab S9", 20000, 11.0, 8000));

        System.out.println("=== DEVICE INFO ===");
        devices.forEach(ElectronicDevice::displayInfo);
        double totalPowerConsumption = devices.stream().mapToDouble(ElectronicDevice::calculatePowerConsumption).sum();
        ElectronicDevice mostExpensive = devices.stream().max(Comparator.comparingDouble(ElectronicDevice::getPrice))
                .orElse(null);
        System.out.println("\nTOTAL POWER CONSUMPTION: " + totalPowerConsumption + " W");

        if (mostExpensive != null) {
            System.out.println("\nMost expensive device:");
            mostExpensive.displayInfo();
        }
    }
}