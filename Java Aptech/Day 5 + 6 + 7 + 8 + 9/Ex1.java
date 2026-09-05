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
        private final String deviceId, deviceName, manufacturer;
        private final int sellingPrice;
        private final LocalDate importDate;

        public ElectronicDevice(String deviceId, String deviceName, String manufacturer, int sellingPrice,
                LocalDate importDate) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.manufacturer = manufacturer;
            this.sellingPrice = sellingPrice;
            this.importDate = importDate;
        }

        public abstract void displayInfo();

        public String getDeviceId() {
            return deviceId;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public String getManufacturer() {
            return manufacturer;
        }

        public int getSellingPrice() {
            return sellingPrice;
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

        public Laptop(String deviceId, String deviceName, String manufacturer, int sellingPrice, double ram,
                double screenSize, LocalDate importDate) {
            super(deviceId, deviceName, manufacturer, sellingPrice, importDate);
            this.ram = ram;
            this.screenSize = screenSize;
            ElectronicDevice.DeviceStatistics.recordLaptop(sellingPrice);
        }

        @Override
        public double calculateDiscountPrice() {
            return getSellingPrice() * 0.85;
        }

        @Override
        public void displayInfo() {
            System.out.println("Laptop\nID: " + getDeviceId() + "\nName: " + getDeviceName() + "\nManufacturer: "
                    + getManufacturer() + "\nPrice: " + getSellingPrice() + "\nRAM: " + ram + "\nScreen size: "
                    + screenSize + "\nImport date: " + getImportDate());
        }

        public double getRam() {
            return ram;
        }

        public double getScreenSize() {
            return screenSize;
        }
    }

    public static class Phone extends ElectronicDevice implements Discountable {
        private final double batteryCapacity;
        private final boolean isSupport5G;

        public Phone(String deviceId, String deviceName, String manufacturer, int sellingPrice, double batteryCapacity,
                boolean isSupport5G, LocalDate importDate) {
            super(deviceId, deviceName, manufacturer, sellingPrice, importDate);
            this.batteryCapacity = batteryCapacity;
            this.isSupport5G = isSupport5G;
            ElectronicDevice.DeviceStatistics.recordPhone(sellingPrice);
        }

        @Override
        public double calculateDiscountPrice() {
            return getSellingPrice() * 0.9;
        }

        @Override
        public void displayInfo() {
            System.out.println("Phone\nID: " + getDeviceId() + "\nName: " + getDeviceName() + "\nManufacturer: "
                    + getManufacturer() + "\nPrice: " + getSellingPrice() + "\nBattery capacity: " + batteryCapacity
                    + "\nSupport 5G: " + (isSupport5G ? "Yes" : "No") + "\nImport date: " + getImportDate());
        }

        public double getBatteryCapacity() {
            return batteryCapacity;
        }

        public boolean isSupport5G() {
            return isSupport5G;
        }
    }

    public static boolean validID(List<ElectronicDevice> devices, String id) {
        return devices.stream().anyMatch(d -> d.getDeviceId().equals(id));
    }

    public static Optional<ElectronicDevice> findByID(List<ElectronicDevice> devices, String id) {
        return devices.stream().filter(d -> d.getDeviceId().equals(id)).findFirst();
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<ElectronicDevice> device = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    ===== ELECTRONIC DEVICE MANAGEMENT =====
                    1. Add device
                    2. Search device by ID
                    3. Phone list with 5G support
                    4. Laptop list with RAM >= 16GB
                    5. Find phone with largest battery capacity
                    6. Find laptop with largest screen size
                    7. Find device with highest price
                    8. Total value of all devices
                    9. Find devices by manufacturer
                    10. Display device names
                    11. Display devices by import year
                    12. Display discounted price of all devices
                    13. Sort devices by name
                    14. Sort devices by price
                    15. Display device statistics
                    0. Exit
                    Enter your choice:\s""");

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
                    int addDevice = -1;
                    while (true) {
                        System.out.print("Add 1)Laptop or 2)Phone: ");
                        try {
                            addDevice = Integer.parseInt(br.readLine());
                            if (addDevice == 1 || addDevice == 2) {
                                break;
                            }
                            System.out.println("Invalid choice");
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid input");
                        }
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

                            System.out.print("Enter manufacturer: ");
                            String manufacturer = br.readLine();

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
                                        System.out.print("Screen size must be greater than 0\nRe-enter screen size: ");
                                        continue;
                                    }
                                    break;
                                } catch (NumberFormatException e) {
                                    System.out.print("Invalid input\nRe-enter screen size: ");
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

                            device.add(new Laptop(id, name, manufacturer, price, ram, screenSize, importDate));
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

                            System.out.print("Enter manufacturer: ");
                            String manufacturer = br.readLine();

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

                            System.out.print("Enter battery capacity: ");
                            double batteryCapacity = 0;
                            while (true) {
                                try {
                                    batteryCapacity = Double.parseDouble(br.readLine());
                                    if (batteryCapacity <= 0) {
                                        throw new InvalidAttributeException("Battery capacity must be greater than 0");
                                    }
                                    break;
                                } catch (NumberFormatException e) {
                                    System.out.print("Invalid input\nRe-enter battery capacity: ");
                                } catch (InvalidAttributeException e) {
                                    System.out.print(e.getMessage() + "\nRe-enter battery capacity: ");
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

                            device.add(new Phone(id, name, manufacturer, price, batteryCapacity, isSupport5G == 1,
                                    importDate));
                            System.out.println("Phone added successful");
                        }
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
                                .max(Comparator.comparingDouble(Phone::getBatteryCapacity)).ifPresentOrElse(p -> {
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
                    device.stream().max(Comparator.comparingInt(ElectronicDevice::getSellingPrice))
                            .ifPresentOrElse(d -> {
                                System.out.println("Device with highest price:");
                                d.displayInfo();
                            }, () -> System.out.println("No device exist"));
                case 8 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        int totalValue = device.stream().mapToInt(ElectronicDevice::getSellingPrice).sum();
                        System.out.println("Total value of all devices: " + totalValue);
                    }
                }
                case 9 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.print("Enter manufacturer: ");
                        String searchManufacturer = br.readLine();
                        List<ElectronicDevice> manufacturerResults = device.stream()
                                .filter(d -> d.getManufacturer().equalsIgnoreCase(searchManufacturer)).toList();

                        if (manufacturerResults.isEmpty()) {
                            System.out.println("No device found for manufacturer: " + searchManufacturer);
                        } else {
                            System.out.println("Devices by manufacturer " + searchManufacturer + ":");
                            manufacturerResults.forEach(ElectronicDevice::displayInfo);
                        }
                    }
                }
                case 10 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.println("Device names:");
                        device.stream().map(ElectronicDevice::getDeviceName).forEach(System.out::println);
                    }
                }
                case 11 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        int year = -1;
                        while (true) {
                            System.out.print("Enter year: ");
                            try {
                                year = Integer.parseInt(br.readLine());
                                break;
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid year");
                            }
                        }
                        final int yearFinal = year;
                        List<ElectronicDevice> yearResults = device.stream()
                                .filter(d -> d.getImportDate().getYear() == yearFinal).toList();

                        if (yearResults.isEmpty()) {
                            System.out.println("No devices imported in " + year);
                        } else {
                            System.out.println("Devices imported in " + year + ":");
                            yearResults.forEach(ElectronicDevice::displayInfo);
                        }
                    }
                }
                case 12 -> {
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        device.forEach(d -> {
                            Discountable discountable = (Discountable) d;
                            System.out.println("ID: " + d.getDeviceId() + "\nName: " + d.getDeviceName()
                                    + "\nOriginal price: " + d.getSellingPrice() + "\nDiscount price: "
                                    + discountable.calculateDiscountPrice() + "\n");
                        });
                    }
                }
                case 13 -> {
                    device.sort(Comparator.comparing(ElectronicDevice::getDeviceName, String.CASE_INSENSITIVE_ORDER));

                    System.out.println("Device list after sorting:");
                    device.forEach(d -> {
                        d.displayInfo();
                        System.out.println();
                    });
                }
                case 14 -> {
                    device.sort(Comparator.comparingInt(ElectronicDevice::getSellingPrice));

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