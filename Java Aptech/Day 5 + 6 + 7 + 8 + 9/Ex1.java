import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Ex1 {
    public static class DeviceNotFoundException extends RuntimeException {
        public DeviceNotFoundException(String message) {
            super(message);
        }
    }

    public static interface Discountable {
        double calculateDiscountPrice();
    }

    public static class DuplicateDeviceIdException extends RuntimeException {
        public DuplicateDeviceIdException(String message) {
            super(message);
        }
    }

    public static abstract class ElectronicDevice {
        private final String ID, name, brand;
        private final int price;
        private final LocalDate importDate;

        public ElectronicDevice(String ID, String name, String brand, int price, LocalDate importDate) {
            this.ID = ID;
            this.name = name;
            this.brand = brand;
            this.price = price;
            this.importDate = importDate;
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

        public LocalDate getImportDate() {
            return importDate;
        }

        public static class DeviceStatistics {
            private static int totalCount = 0, phoneCount = 0, laptopCount = 0;
            private static long priceSum = 0;

            private static void recordPhone(int price) {
                totalCount++;
                phoneCount++;
                priceSum += price;
            }

            private static void recordLaptop(int price) {
                totalCount++;
                laptopCount++;
                priceSum += price;
            }

            public static long totalDevice() {
                return totalCount;
            }

            public static long totalPhone() {
                return phoneCount;
            }

            public static long totalLaptop() {
                return laptopCount;
            }

            public static double averagePrice() {
                return totalCount == 0 ? 0 : (double) priceSum / totalCount;
            }
        }
    }

    public static class InvalidAttributeException extends RuntimeException {
        public InvalidAttributeException(String message) {
            super(message);
        }
    }

    public static class InvalidPriceException extends RuntimeException {
        public InvalidPriceException(String message) {
            super(message);
        }
    }

    public static class Laptop extends ElectronicDevice implements Discountable {
        private final double ram, screenSize;

        public Laptop(String ID, String name, String brand, int price, double ram, double screenSize,
                LocalDate importDate) {
            super(ID, name, brand, price, importDate);
            this.ram = ram;
            this.screenSize = screenSize;
            ElectronicDevice.DeviceStatistics.recordLaptop(price);
        }

        @Override
        public double calculateDiscountPrice() {
            return getPrice() * 0.85;
        }

        @Override
        public void displayInfo() {
            System.out.println("Laptop\nID: " + getID() + "\nName: " + getName() + "\nBrand: " + getBrand()
                    + "\nPrice: " + getPrice() + "\nRAM: " + ram + "\nScreen size: " + screenSize + "\nImport date: "
                    + getImportDate());
        }

        public double getRam() {
            return ram;
        }

        public double getScreenSize() {
            return screenSize;
        }
    }

    public static class Phone extends ElectronicDevice implements Discountable {
        private final double batteryLife;
        private final boolean isSupport5G;

        public Phone(String ID, String name, String brand, int price, double batteryLife, boolean isSupport5G,
                LocalDate importDate) {
            super(ID, name, brand, price, importDate);
            this.batteryLife = batteryLife;
            this.isSupport5G = isSupport5G;
            ElectronicDevice.DeviceStatistics.recordPhone(price);
        }

        @Override
        public double calculateDiscountPrice() {
            return getPrice() * 0.9;
        }

        @Override
        public void displayInfo() {
            System.out.println("Phone\nID: " + getID() + "\nName: " + getName() + "\nBrand: " + getBrand() + "\nPrice: "
                    + getPrice() + "\nBattery life: " + batteryLife + "\nSupport 5G: " + (isSupport5G ? "Yes" : "No")
                    + "\nImport date: " + getImportDate());
        }

        public double getBatteryLife() {
            return batteryLife;
        }

        public boolean isSupport5G() {
            return isSupport5G;
        }
    }

    public static boolean validID(List<ElectronicDevice> devices, String id) {
        return devices.stream().anyMatch(d -> d.getID().equals(id));
    }

    public static Optional<ElectronicDevice> findByID(List<ElectronicDevice> devices, String id) {
        return devices.stream().filter(d -> d.getID().equals(id)).findFirst();
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<ElectronicDevice> device = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print(
                    "===== ELECTRONIC DEVICE MANAGEMENT =====\n1. Add device\n2. Search device by ID\n3. Phone list with 5G support\n4. Laptop list with RAM >= 16GB\n5. Find phone with largest battery capacity\n6. Find laptop with largest screen size\n7. Find device with highest price\n8. Total value of all devices\n9. Find devices by manufacturer\n10. Display device names\n11. Display devices by import year\n12. Display discounted price of all devices\n13. Sort devices by name\n14. Sort devices by price\n15. Display device statistics\n0. Exit\nEnter your choice: ");

            int choice;
            try {
                choice = Integer.parseInt(br.readLine());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input");
                continue;
            }

            switch (choice) {
                case 0 -> isExit = true;
                case 1 -> {
                    System.out.print("Add 1)Laptop or 2)Phone: ");
                    int addDevice;
                    try {
                        addDevice = Integer.parseInt(br.readLine());
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid input");
                        addDevice = -1;
                    }

                    switch (addDevice) {
                        case 1 -> {
                            System.out.print("Enter ID: ");
                            String id = null;
                            while (true) {
                                try {
                                    id = br.readLine();
                                    if (validID(device, id)) {
                                        throw new DuplicateDeviceIdException("ID " + id + " already exists");
                                    }
                                    break;
                                } catch (DuplicateDeviceIdException e) {
                                    System.out.print(e.getMessage() + "\nRe-enter ID: ");
                                }
                            }

                            System.out.print("Enter name: ");
                            String name = br.readLine();

                            System.out.print("Enter brand: ");
                            String brand = br.readLine();

                            System.out.print("Enter price: ");
                            int price = 0;
                            while (true) {
                                try {
                                    price = Integer.parseInt(br.readLine());
                                    if (price <= 0) {
                                        throw new InvalidPriceException("Price must be greater than 0");
                                    }
                                    break;
                                } catch (NumberFormatException e) {
                                    System.out.print("Invalid input\nRe-enter price: ");
                                } catch (InvalidPriceException e) {
                                    System.out.print(e.getMessage() + "\nRe-enter price: ");
                                }
                            }

                            System.out.print("Enter RAM: ");
                            double ram = 0;
                            while (true) {
                                try {
                                    ram = Double.parseDouble(br.readLine());
                                    if (ram <= 0) {
                                        throw new InvalidAttributeException("RAM must be greater than 0");
                                    }
                                    break;
                                } catch (NumberFormatException e) {
                                    System.out.print("Invalid input\nRe-enter RAM: ");
                                } catch (InvalidAttributeException e) {
                                    System.out.print(e.getMessage() + "\nRe-enter RAM: ");
                                }
                            }

                            System.out.print("Enter screen size: ");
                            double screenSize = 0;
                            while (true) {
                                try {
                                    screenSize = Double.parseDouble(br.readLine());
                                    if (screenSize <= 0) {
                                        throw new InvalidAttributeException("Screen size must be greater than 0");
                                    }
                                    break;
                                } catch (NumberFormatException e) {
                                    System.out.print("Invalid input\nRe-enter screen size: ");
                                } catch (InvalidAttributeException e) {
                                    System.out.print(e.getMessage() + "\nRe-enter screen size: ");
                                }
                            }

                            System.out.print("Enter import date (yyyy-MM-dd): ");
                            LocalDate importDate = null;
                            while (importDate == null) {
                                try {
                                    importDate = LocalDate.parse(br.readLine());
                                } catch (DateTimeParseException e) {
                                    System.out.print("Invalid format. Use yyyy-MM-dd\nRe-enter import date: ");
                                }
                            }

                            device.add(new Laptop(id, name, brand, price, ram, screenSize, importDate));
                            System.out.println("Laptop added successful");
                        }
                        case 2 -> {
                            System.out.print("Enter ID: ");
                            String id = null;
                            while (true) {
                                try {
                                    id = br.readLine();
                                    if (validID(device, id)) {
                                        throw new DuplicateDeviceIdException("ID " + id + " already exists");
                                    }
                                    break;
                                } catch (DuplicateDeviceIdException e) {
                                    System.out.print(e.getMessage() + "\nRe-enter ID: ");
                                }
                            }

                            System.out.print("Enter name: ");
                            String name = br.readLine();

                            System.out.print("Enter brand: ");
                            String brand = br.readLine();

                            System.out.print("Enter price: ");
                            int price = 0;
                            while (true) {
                                try {
                                    price = Integer.parseInt(br.readLine());
                                    if (price <= 0) {
                                        throw new InvalidPriceException("Price must be greater than 0");
                                    }
                                    break;
                                } catch (NumberFormatException e) {
                                    System.out.print("Invalid input\nRe-enter price: ");
                                } catch (InvalidPriceException e) {
                                    System.out.print(e.getMessage() + "\nRe-enter price: ");
                                }
                            }

                            System.out.print("Enter battery life: ");
                            double batteryLife = 0;
                            while (true) {
                                try {
                                    batteryLife = Double.parseDouble(br.readLine());
                                    if (batteryLife <= 0) {
                                        throw new InvalidAttributeException("Battery life must be greater than 0");
                                    }
                                    break;
                                } catch (NumberFormatException e) {
                                    System.out.print("Invalid input\nRe-enter battery life: ");
                                } catch (InvalidAttributeException e) {
                                    System.out.print(e.getMessage() + "\nRe-enter battery life: ");
                                }
                            }

                            int isSupport5G;
                            while (true) {
                                System.out.print("Is it support 5G 1)Yes 0)No: ");
                                try {
                                    isSupport5G = Integer.parseInt(br.readLine());
                                    if (isSupport5G == 0 || isSupport5G == 1) {
                                        break;
                                    }
                                    System.out.println("Invalid choice");
                                } catch (NumberFormatException e) {
                                    System.out.println("Invalid input");
                                }
                            }

                            System.out.print("Enter import date (yyyy-MM-dd): ");
                            LocalDate importDate = null;
                            while (importDate == null) {
                                try {
                                    importDate = LocalDate.parse(br.readLine());
                                } catch (DateTimeParseException e) {
                                    System.out.print("Invalid format. Use yyyy-MM-dd\nRe-enter import date: ");
                                }
                            }

                            device.add(new Phone(id, name, brand, price, batteryLife, isSupport5G == 1, importDate));
                            System.out.println("Phone added successful");
                        }
                        default -> System.out.println("Invalid choice");
                    }
                }
                case 2 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.print("Enter ID: ");
                        String searchID = br.readLine();

                        findByID(device, searchID).ifPresentOrElse(ElectronicDevice::displayInfo,
                                () -> System.out.println("Device with ID " + searchID + " not found"));
                    }
                }
                case 3 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.println("Phone with 5G support: ");
                        device.stream().filter(Phone.class::isInstance).map(Phone.class::cast)
                                .filter(Phone::isSupport5G).forEach(Phone::displayInfo);
                    }
                }
                case 4 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.println("Laptop with RAM >= 16GB: ");
                        device.stream().filter(Laptop.class::isInstance).map(Laptop.class::cast)
                                .filter(laptop -> laptop.getRam() >= 16).forEach(Laptop::displayInfo);
                    }
                }
                case 5 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        device.stream().filter(Phone.class::isInstance).map(Phone.class::cast)
                                .max(Comparator.comparingDouble(Phone::getBatteryLife)).ifPresentOrElse(p -> {
                                    System.out.println("Phone with largest battery capacity:");
                                    p.displayInfo();
                                }, () -> System.out.println("No phones found"));
                    }
                }
                case 6 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        device.stream().filter(Laptop.class::isInstance).map(Laptop.class::cast)
                                .max(Comparator.comparingDouble(Laptop::getScreenSize)).ifPresentOrElse(l -> {
                                    System.out.println("Laptop with largest screen size:");
                                    l.displayInfo();
                                }, () -> System.out.println("No laptops found"));
                    }
                }
                case 7 ->
                    device.stream().max(Comparator.comparingInt(ElectronicDevice::getPrice)).ifPresentOrElse(d -> {
                        System.out.println("Device with highest price:");
                        d.displayInfo();
                    }, () -> System.out.println("No device exist"));
                case 8 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        int totalValue = device.stream().mapToInt(ElectronicDevice::getPrice).sum();
                        System.out.println("Total value of all devices: " + totalValue);
                    }
                }
                case 9 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.print("Enter brand: ");
                        String searchBrand = br.readLine();
                        List<ElectronicDevice> brandResults = device.stream()
                                .filter(d -> d.getBrand().equalsIgnoreCase(searchBrand)).toList();

                        if (brandResults.isEmpty()) {
                            System.out.println("No device found for brand: " + searchBrand);
                        } else {
                            System.out.println("Devices by brand " + searchBrand + ":");
                            brandResults.forEach(ElectronicDevice::displayInfo);
                        }
                    }
                }
                case 10 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.println("Device names:");
                        device.stream().map(ElectronicDevice::getName).forEach(System.out::println);
                    }
                }
                case 11 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.print("Enter year: ");
                        try {
                            int year = Integer.parseInt(br.readLine());
                            List<ElectronicDevice> yearResults = device.stream()
                                    .filter(d -> d.getImportDate().getYear() == year).toList();

                            if (yearResults.isEmpty()) {
                                System.out.println("No devices imported in " + year);
                            } else {
                                System.out.println("Devices imported in " + year + ":");
                                yearResults.forEach(ElectronicDevice::displayInfo);
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid year");
                        }
                    }
                }
                case 12 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        device.stream().forEach(d -> {
                            Discountable discountable = (Discountable) d;
                            System.out.println(
                                    "ID: " + d.getID() + "\nName: " + d.getName() + "\nOriginal price: " + d.getPrice()
                                            + "\nDiscount price: " + discountable.calculateDiscountPrice() + "\n");
                        });
                    }
                }
                case 13 -> {
                    device.sort(Comparator.comparing(ElectronicDevice::getName, String.CASE_INSENSITIVE_ORDER));

                    System.out.println("Device list after sorting:");
                    device.forEach(d -> {
                        d.displayInfo();
                        System.out.println();
                    });
                }
                case 14 -> {
                    device.sort(Comparator.comparingInt(ElectronicDevice::getPrice));

                    System.out.println("Device list sorted by price (ascending):");
                    device.forEach(d -> {
                        d.displayInfo();
                        System.out.println();
                    });
                }
                case 15 ->
                    System.out.println("Total number of devices: " + ElectronicDevice.DeviceStatistics.totalDevice()
                            + "\nTotal number of phones: " + ElectronicDevice.DeviceStatistics.totalPhone()
                            + "\nTotal number of laptops: " + ElectronicDevice.DeviceStatistics.totalLaptop()
                            + "\nAverage price: " + ElectronicDevice.DeviceStatistics.averagePrice());
                default -> System.out.println("Invalid choice");
            }
        }
    }
}