import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import module.Car;
import module.Vehicle;
import module.Car.FUEL_TYPE;
import module.Motorcycle;

public class Main {
    public static boolean validID(List<Vehicle> vehicles, String ID) {
        for (Vehicle vehicle : vehicles) {
            if (vehicle.getID().equals(ID)) {
                return true;
            }
        }
        return false;
    }

    public static boolean validFuelType(String type) {
        for (FUEL_TYPE fuel : FUEL_TYPE.values()) {
            if (fuel.name().equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<Vehicle> vehicles = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print(
                    "1. Add a Car\n2. Add a Motorcycle\n3. Display All Vehicles\n4. Search for a Vehicle by ID\n5. Display All Electric Cars\n6. Display All Motorcycles with ABS\n0. Exit\nEnter your choice: ");
            int choice = Integer.parseInt(br.readLine());

            switch (choice) {
                case 0:
                    isExit = true;
                    break;
                case 1:
                    System.out.print("Enter ID: ");
                    String ID;
                    while (true) {
                        ID = br.readLine();
                        if (!validID(vehicles, ID)) {
                            break;
                        }
                        System.out.print("Invalid ID\nRe-enter ID: ");
                    }

                    System.out.print("Enter name: ");
                    String name = br.readLine();

                    System.out.print("Enter manufacturer: ");
                    String manufacturer = br.readLine();

                    System.out.print("Enter value: ");
                    int value;
                    while (true) {
                        value = Integer.parseInt(br.readLine());
                        if (value > 0) {
                            break;
                        }
                        System.out.print("Value cant be negative\nRe-enter value: ");
                    }

                    System.out.print("Enter number of seat: ");
                    int numberOfSeat;
                    while (true) {
                        numberOfSeat = Integer.parseInt(br.readLine());
                        if (numberOfSeat > 0) {
                            break;
                        }
                        System.out.print("Number of seat cant be negative\nRe-enter number of seat: ");
                    }

                    System.out.print("Enter fuel type: ");
                    String fuelType;
                    while (true) {
                        fuelType = br.readLine();
                        if (validFuelType(fuelType)) {
                            break;
                        }
                        System.out.print("Invalid fuel type\nRe-enter fuel type: ");
                    }

                    FUEL_TYPE selectedFuel = null;

                    for (FUEL_TYPE fuel : FUEL_TYPE.values()) {
                        if (fuel.name().equalsIgnoreCase(fuelType)) {
                            selectedFuel = fuel;
                            break;
                        }
                    }

                    vehicles.add(new Car(ID, name, manufacturer, value, numberOfSeat, selectedFuel));
                    break;
                case 2:
                    System.out.print("Enter ID: ");
                    while (true) {
                        ID = br.readLine();
                        if (!validID(vehicles, ID)) {
                            break;
                        }
                        System.out.print("Invalid ID\nRe-enter ID: ");
                    }

                    System.out.print("Enter name: ");
                    name = br.readLine();

                    System.out.print("Enter manufacturer: ");
                    manufacturer = br.readLine();

                    System.out.print("Enter value: ");
                    while (true) {
                        value = Integer.parseInt(br.readLine());
                        if (value > 0) {
                            break;
                        }
                        System.out.print("Value cant be negative\nRe-enter value: ");
                    }

                    System.out.print("Enter engine capacity: ");
                    double engineCapacity;
                    while (true) {
                        engineCapacity = Double.parseDouble(br.readLine());
                        if (engineCapacity > 0) {
                            break;
                        }
                        System.out.print("Engine capacity cant be negative\nRe-enter engine capacity: ");
                    }

                    System.out.print("Enter ABS support 1)Yes 2)No: ");
                    int ABSSupport;
                    while (true) {
                        ABSSupport = Integer.parseInt(br.readLine());
                        if (ABSSupport == 1 || ABSSupport == 2) {
                            break;
                        }
                        System.out.print("Invalid option\nRe-enter ABS support 1)Yes 2)No: ");
                    }

                    vehicles.add(new Motorcycle(ID, name, manufacturer, value, engineCapacity, ABSSupport == 1));
                    break;
                case 3:
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        for (Vehicle vehicle : vehicles) {
                            if (vehicle instanceof Car car) {
                                System.out.println("Car" + car.display());
                            } else if (vehicle instanceof Motorcycle motorcycle) {
                                System.out.println("Motorcycle" + motorcycle.display());
                            }
                        }
                    }
                    break;
                case 4:
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        System.out.print("Enter ID: ");
                        ID = br.readLine();

                        boolean found = false;

                        for (Vehicle vehicle : vehicles) {
                            if (vehicle.getID().equals(ID)) {
                                if (vehicle instanceof Car car) {
                                    System.out.println("Car" + car.display());
                                } else if (vehicle instanceof Motorcycle motorcycle) {
                                    System.out.println("Motorcycle" + motorcycle.display());
                                }

                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            System.out.println("Vehicle not found");
                        }
                    }
                    break;
                case 5:
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        for (Vehicle vehicle : vehicles) {
                            if (vehicle instanceof Car car) {
                                if (car.getFuelType() == FUEL_TYPE.Electric) {
                                    System.out.println("Car" + car.display());
                                }
                            }
                        }
                    }
                    break;
                case 6:
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        for (Vehicle vehicle : vehicles) {
                            if (vehicle instanceof Motorcycle motorcycle) {
                                if (motorcycle.isABSSupported()) {
                                    System.out.println("Motorcycle" + motorcycle.display());
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