import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import module.DeviceNotFoundException;
import module.Discountable;
import module.DuplicateDeviceIdException;
import module.ElectronicDevice;
import module.InvalidBatteryLifeException;
import module.InvalidPriceException;
import module.InvalidRamException;
import module.Laptop;
import module.Phone;

public class Main {
    public static boolean validID(List<ElectronicDevice> devices, String id) {
        for (ElectronicDevice device : devices) {
            if (device.getID().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<ElectronicDevice> device = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print(
                    "===== ELECTRONIC DEVICE MANAGEMENT =====\n1. Add device\n2. Search device\n3. Phone list with 5G support\n4. Laptop list with RAM >= 16GB\n5. Display discount prices\n6. Sort devices by name\n7. Statistic\n8. Find device with highest price\n9. Total value of all devices\n10. Find devices by brand\n11. Sort devices by price\n12. Display device names\n0. Exit\nEnter your choice: ");

            int choice = -1;
            try {
                choice = Integer.parseInt(br.readLine());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input");
                continue;
            }

            switch (choice) {
                case 0:
                    isExit = true;
                    break;
                case 1:
                    System.out.print("Add 1)Laptop or 2)Phone: ");
                    int addDevice = 0;
                    try {
                        addDevice = Integer.parseInt(br.readLine());
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid input");
                        break;
                    }

                    switch (addDevice) {
                        case 1:
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
                                        throw new InvalidRamException("RAM must be greater than 0");
                                    }
                                    break;
                                } catch (NumberFormatException e) {
                                    System.out.print("Invalid input\nRe-enter RAM: ");
                                } catch (InvalidRamException e) {
                                    System.out.print(e.getMessage() + "\nRe-enter RAM: ");
                                }
                            }

                            System.out.print("Enter screen size: ");
                            double screenSize = 0;
                            while (true) {
                                try {
                                    screenSize = Double.parseDouble(br.readLine());
                                    if (screenSize <= 0) {
                                        throw new IllegalArgumentException("Screen size must be greater than 0");
                                    }
                                    break;
                                } catch (NumberFormatException e) {
                                    System.out.print("Invalid input\nRe-enter screen size: ");
                                } catch (IllegalArgumentException e) {
                                    System.out.print(e.getMessage() + "\nRe-enter screen size: ");
                                }
                            }
                            device.add(new Laptop(id, name, brand, price, ram, screenSize));
                            System.out.println("Laptop added successful");
                            break;
                        case 2:
                            System.out.print("Enter ID: ");
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
                            name = br.readLine();

                            System.out.print("Enter brand: ");
                            brand = br.readLine();

                            System.out.print("Enter price: ");
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
                                        throw new InvalidBatteryLifeException("Battery life must be greater than 0");
                                    }
                                    break;
                                } catch (NumberFormatException e) {
                                    System.out.print("Invalid input\nRe-enter battery life: ");
                                } catch (InvalidBatteryLifeException e) {
                                    System.out.print(e.getMessage() + "\nRe-enter battery life: ");
                                }
                            }

                            int isSupport5G = -1;
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
                            device.add(new Phone(id, name, brand, price, batteryLife, isSupport5G == 1));
                            break;
                        default:
                            System.out.println("Invalid choice");
                            break;
                    }
                    break;
                case 2:
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.print("Enter ID: ");
                        String id = null;
                        while (true) {
                            try {
                                id = br.readLine();
                                if (!validID(device, id)) {
                                    throw new DeviceNotFoundException("Device with ID " + id + " not found");
                                }
                                break;
                            } catch (DeviceNotFoundException e) {
                                System.out.print(e.getMessage() + "\nRe-enter ID: ");
                            }
                        }

                        for (ElectronicDevice electronicDevice : device) {
                            if (electronicDevice.getID().equals(id)) {
                                System.out.println(electronicDevice.getClass().getSimpleName() + "\nID: "
                                        + electronicDevice.getID() + "\nName: " + electronicDevice.getName()
                                        + "\nBrand: "
                                        + electronicDevice.getBrand() + "\nPrice: " + electronicDevice.getPrice());
                                if (electronicDevice instanceof Laptop laptop) {
                                    System.out.println(
                                            "RAM: " + laptop.getRam() + "\nScreen size: " + laptop.getScreenSize());
                                } else if (electronicDevice instanceof Phone phone) {
                                    System.out.println("Battery life: " + phone.getBatteryLife() + "\nSupport 5G: "
                                            + (phone.isSupport5G() ? "Yes" : "No"));
                                }
                                break;
                            }
                        }
                    }
                    break;
                case 3:
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.println("Phone with 5G support: ");
                        device.stream()
                                .filter(d -> d instanceof Phone)
                                .map(d -> (Phone) d)
                                .filter(Phone::isSupport5G)
                                .forEach(phone -> System.out.println("\nID: " + phone.getID() + "\nName: "
                                        + phone.getName() + "\nBrand: " + phone.getBrand() + "\nPrice: "
                                        + phone.getPrice() + "\nBattery life: " + phone.getBatteryLife()
                                        + "\nSupport 5G: Yes"));
                    }
                    break;
                case 4:
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.println("Laptop with RAM >= 16GB: ");
                        device.stream()
                                .filter(d -> d instanceof Laptop)
                                .map(d -> (Laptop) d)
                                .filter(laptop -> laptop.getRam() >= 16)
                                .forEach(laptop -> System.out.println("\nID: " + laptop.getID() + "\nName: "
                                        + laptop.getName() + "\nBrand: " + laptop.getBrand() + "\nPrice: "
                                        + laptop.getPrice() + "\nRAM: " + laptop.getRam() + "\nScreen size: "
                                        + laptop.getScreenSize()));
                    }
                    break;
                case 5:
                    for (ElectronicDevice d : device) {
                        Discountable discountable = (Discountable) d;

                        System.out.println("ID: " + d.getID());
                        System.out.println("Name: " + d.getName());
                        System.out.println("Original price: " + d.getPrice());
                        System.out.println("Discount price: "
                                + discountable.calculateDiscountPrice());
                        System.out.println();
                    }
                    break;
                case 6:
                    device.sort((d1, d2) -> d1.getName().compareToIgnoreCase(d2.getName()));

                    System.out.println("Device list after sorting:");
                    for (ElectronicDevice d : device) {
                        d.displayInfo();
                        System.out.println();
                    }
                    break;
                case 7:
                    System.out.println("Total number of devices: "
                            + ElectronicDevice.DeviceStatistics.totalDevice(device) + "\nTotal number of phones: "
                            + ElectronicDevice.DeviceStatistics.totalPhone(device) + "\nTotal number of laptops: "
                            + ElectronicDevice.DeviceStatistics.totalLaptop(device) + "\nAverage price: "
                            + ElectronicDevice.DeviceStatistics.averagePrice(device));
                    break;
                case 8:
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        device.stream()
                                .max((d1, d2) -> Integer.compare(d1.getPrice(), d2.getPrice()))
                                .ifPresent(d -> {
                                    System.out.println("Device with highest price:");
                                    d.displayInfo();
                                });
                    }
                    break;
                case 9:
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        int totalValue = device.stream()
                                .mapToInt(ElectronicDevice::getPrice)
                                .sum();
                        System.out.println("Total value of all devices: " + totalValue);
                    }
                    break;
                case 10:
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.print("Enter brand: ");
                        String searchBrand = br.readLine();

                        List<ElectronicDevice> brandResults = device.stream()
                                .filter(d -> d.getBrand().equalsIgnoreCase(searchBrand))
                                .toList();

                        if (brandResults.isEmpty()) {
                            System.out.println("No device found for brand: " + searchBrand);
                        } else {
                            System.out.println("Devices by brand " + searchBrand + ":");
                            brandResults.forEach(ElectronicDevice::displayInfo);
                        }
                    }
                    break;
                case 11:
                    device.sort((d1, d2) -> Integer.compare(d1.getPrice(), d2.getPrice()));

                    System.out.println("Device list sorted by price (ascending):");
                    for (ElectronicDevice d : device) {
                        d.displayInfo();
                        System.out.println();
                    }
                    break;
                case 12:
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.println("Device names:");
                        device.stream()
                                .map(ElectronicDevice::getName)
                                .forEach(System.out::println);
                    }
                    break;
                default:
                    System.out.println("Invalid choice");
                    break;
            }
        }
    }
}