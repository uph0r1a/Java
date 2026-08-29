import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class Ex1 {
    public static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static interface IElectronicDevice {
        public void inputDevice();

        public void displayDevice();
    }

    static class InvalidDeviceDataException extends Exception {
        public InvalidDeviceDataException(String message) {
            super(message);
        }
    }

    public static class ElectronicDevice implements IElectronicDevice {
        private String name, brand;
        private int releaseYear;
        private double price;

        public ElectronicDevice() {
        }

        public ElectronicDevice(String name, String brand, int releaseYear, double price) {
            this.name = name;
            this.brand = brand;
            this.releaseYear = releaseYear;
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }

        public int getReleaseYear() {
            return releaseYear;
        }

        public void setReleaseYear(int releaseYear) {
            this.releaseYear = releaseYear;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public void inputDevice() {
            System.out.print("Enter name: ");
            String name;
            while (true) {
                try {
                    name = br.readLine();
                    if (name.length() >= 2) {
                        break;
                    }
                    throw new InvalidDeviceDataException("Device name must be at least 2 characters.");
                } catch (InvalidDeviceDataException e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter name: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter name: ");
                }
            }

            System.out.print("Enter brand: ");
            String brand;
            while (true) {
                try {
                    brand = br.readLine();
                    if (brand.length() >= 2) {
                        break;
                    }
                    throw new InvalidDeviceDataException("Device brand must be at least 2 characters.");
                } catch (InvalidDeviceDataException e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter brand: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter brand: ");
                }
            }

            System.out.print("Enter release year: ");
            int releaseYear;
            while (true) {
                try {
                    releaseYear = Integer.parseInt(br.readLine());
                    if (releaseYear >= 2000 && releaseYear <= Year.now().getValue()) {
                        break;
                    }
                    throw new InvalidDeviceDataException(
                            "Device release year must be between 2000 and " + Year.now().getValue());
                } catch (NumberFormatException e) {
                    System.out.print("Error: release year must be a whole number.\nRe-enter release year: ");
                } catch (InvalidDeviceDataException e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter release year: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter price: ");
            double price;
            while (true) {
                try {
                    price = Double.parseDouble(br.readLine());
                    if (price > 0) {
                        break;
                    }
                    throw new InvalidDeviceDataException("Device price must be positive.");
                } catch (NumberFormatException e) {
                    System.out.print("Error: price must be a valid number.\nRe-enter price: ");
                } catch (InvalidDeviceDataException e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter price: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            setName(name);
            setBrand(brand);
            setReleaseYear(releaseYear);
            setPrice(price);
        }

        public void displayDevice() {
            System.out.println("Device name: " + getName() + "\nDevice brand: " + getBrand() + "\nDevice release year: "
                    + getReleaseYear() + "\nDevice price: " + getPrice() + "\nDevice '" + getName() + "'"
                    + (isPremium() ? " is a premium product.\n" : " is standard.\n"));
        }

        public boolean isPremium() {
            return getPrice() > 1000;
        }
    }

    public static class InventoryManager {
        private final List<ElectronicDevice> devices = new ArrayList<>();

        public void addDevice() {
            ElectronicDevice device = new ElectronicDevice();
            device.inputDevice();
            devices.add(device);
        }

        public void showAllDevices() {
            for (ElectronicDevice electronicDevice : devices) {
                electronicDevice.displayDevice();
            }
        }

        public void findMostExpensive() {
            if (devices.isEmpty()) {
                System.out.println("No devices in inventory yet.");
                return;
            }

            double maxPrice = 0;
            ElectronicDevice expensive = null;
            for (ElectronicDevice electronicDevice : devices) {
                if (expensive == null || electronicDevice.getPrice() > maxPrice) {
                    maxPrice = electronicDevice.getPrice();
                    expensive = electronicDevice;
                }
            }
            expensive.displayDevice();
        }
    }

    public static void main(String[] args) {
        boolean isExit = false;
        InventoryManager inventoryManager = new InventoryManager();
        while (!isExit) {
            System.out.print("""
                    === ELECTRONICS STORE MENU ===
                    1. Add New Device
                    2. Show All Devices
                    3. Find Most Expensive Device
                    4. Exit
                    Your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 1 && choice <= 4) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage());
                }
            }

            switch (choice) {
                case 1 -> inventoryManager.addDevice();
                case 2 -> inventoryManager.showAllDevices();
                case 3 -> inventoryManager.findMostExpensive();
                case 4 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}