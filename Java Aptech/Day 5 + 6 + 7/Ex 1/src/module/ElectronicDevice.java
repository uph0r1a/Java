package module;

import java.util.List;

public abstract class ElectronicDevice {
    private final String ID, name, brand;
    private final int price;

    public ElectronicDevice(String ID, String name, String brand, int price) {
        this.ID = ID;
        this.name = name;
        this.brand = brand;
        this.price = price;
    }

    public abstract void displayInfo();

    public String getID() {
        return ID;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public int getPrice() {
        return price;
    }

    public static class DeviceStatistics {
        public static int totalDevice(List<ElectronicDevice> devices) {
            if (devices.isEmpty()) {
                return 0;
            } else {
                return devices.size();
            }
        }

        public static int totalPhone(List<ElectronicDevice> devices) {
            if (devices.isEmpty()) {
                return 0;
            } else {
                int count = 0;

                for (ElectronicDevice electronicDevice : devices) {
                    if (electronicDevice.getClass().getSimpleName().toLowerCase().equalsIgnoreCase("Phone")) {
                        count++;
                    }
                }
                return count;
            }
        }

        public static int totalLaptop(List<ElectronicDevice> devices) {
            if (devices.isEmpty()) {
                return 0;
            } else {
                int count = 0;

                for (ElectronicDevice electronicDevice : devices) {
                    if (electronicDevice.getClass().getSimpleName().toLowerCase().equalsIgnoreCase("Laptop")) {
                        count++;
                    }
                }
                return count;
            }
        }

        public static double averagePrice(List<ElectronicDevice> devices) {
            if (devices.isEmpty()) {
                return 0;
            } else {
                int priceSum = 0;

                for (ElectronicDevice electronicDevice : devices) {
                    priceSum += electronicDevice.getPrice();
                }
                return (double) priceSum / devices.size();
            }
        }
    }
}