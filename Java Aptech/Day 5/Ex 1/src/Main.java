import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import module.ElectronicDevice;
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
                    "===== ELECTRONIC DEVICE MANAGEMENT =====\n1. Add device\n2. Search device\n3. Phone list with 5G support\n4. Laptop list with RAM >= 16GB\n0. Exit\nEnter your choice: ");
            int choice = Integer.parseInt(br.readLine());

            switch (choice) {
                case 0:
                    isExit = true;
                    break;
                case 1:
                    System.out.print("Add 1)Laptop or 2)Phone: ");
                    int addDevice = Integer.parseInt(br.readLine());

                    switch (addDevice) {
                        case 1:
                            System.out.print("Enter ID: ");
                            String id;
                            while (true) {
                                id = br.readLine();
                                if (!validID(device, id)) {
                                    break;
                                }
                                System.out.print("Invalid ID\nRe-enter ID: ");
                            }

                            System.out.print("Enter name: ");
                            String name = br.readLine();

                            System.out.print("Enter brand: ");
                            String brand = br.readLine();

                            System.out.print("Enter price: ");
                            int price;
                            while (true) {
                                price = Integer.parseInt(br.readLine());
                                if (price > 0) {
                                    break;
                                }
                                System.out.print("Price cant be negative\nRe-enter price: ");
                            }

                            System.out.print("Enter RAM: ");
                            double ram;
                            while (true) {
                                ram = Double.parseDouble(br.readLine());
                                if (ram > 0) {
                                    break;
                                }
                                System.out.print("RAM cant be negative\nRe-enter RAM: ");
                            }

                            System.out.print("Enter screen size: ");
                            double screenSize;
                            while (true) {
                                screenSize = Double.parseDouble(br.readLine());
                                if (screenSize > 0) {
                                    break;
                                }
                                System.out.print("Screen size cant be negative\nRe-enter screen size: ");
                            }
                            device.add(new Laptop(id, name, brand, price, ram, screenSize));
                            System.out.println("Laptop added successful");
                            break;
                        case 2:
                            System.out.print("Enter ID: ");
                            while (true) {
                                id = br.readLine();
                                if (!validID(device, id)) {
                                    break;
                                }
                                System.out.print("Invalid ID\nRe-enter ID: ");
                            }

                            System.out.print("Enter name: ");
                            name = br.readLine();

                            System.out.print("Enter brand: ");
                            brand = br.readLine();

                            System.out.print("Enter price: ");
                            while (true) {
                                price = Integer.parseInt(br.readLine());
                                if (price > 0) {
                                    break;
                                }
                                System.out.print("Price cant be negative\nRe-enter price: ");
                            }

                            System.out.print("Enter battery life: ");
                            double batteryLife;
                            while (true) {
                                batteryLife = Double.parseDouble(br.readLine());
                                if (batteryLife > 0) {
                                    break;
                                }
                                System.out.print("Battery life cant be negative\nRe-enter battery life: ");
                            }

                            int isSupport5G;
                            while (true) {
                                System.out.print("Is it support 5G 1)Yes 0)No: ");
                                isSupport5G = Integer.parseInt(br.readLine());
                                if (isSupport5G == 0 || isSupport5G == 1) {
                                    break;
                                }
                                System.out.println("Invalid choice");
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
                        String id;
                        while (true) {
                            id = br.readLine();
                            if (validID(device, id)) {
                                break;
                            }
                            System.out.print("Invalid ID\nRe-enter ID: ");
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
                        for (ElectronicDevice electronicDevice : device) {
                            if (electronicDevice instanceof Phone phone) {
                                if (phone.isSupport5G()) {
                                    System.out.println("\nID: " + phone.getID() + "\nName: " + phone.getName()
                                            + "\nBrand: "
                                            + phone.getBrand() + "\nPrice: " + phone.getPrice() + "\nBattery life: "
                                            + phone.getBatteryLife() + "\nSupport 5G: "
                                            + (phone.isSupport5G() ? "Yes" : "No"));
                                }
                            }
                        }
                    }
                    break;
                case 4:
                    if (device.isEmpty()) {
                        System.out.println("No device exist");
                    } else {
                        System.out.println("Laptop with RAM >= 16GB: ");
                        for (ElectronicDevice electronicDevice : device) {
                            if (electronicDevice instanceof Laptop laptop) {
                                if (laptop.getRam() >= 16) {
                                    System.out.println("\nID: " + laptop.getID() + "\nName: " + laptop.getName()
                                            + "\nBrand: " + laptop.getBrand() + "\nPrice: " + laptop.getPrice()
                                            + "\nRAM: "
                                            + laptop.getRam() + "\nScreen size: " + laptop.getScreenSize());
                                }
                            }
                        }
                    }
                    break;
                default:
                    System.out.println("Invalid choice");
                    break;
            }
        }
    }
}