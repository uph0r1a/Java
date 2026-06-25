import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import module.Car;
import module.DuplicateVehicleIdException;
import module.InvalidVehicleValueException;
import module.Vehicle;
import module.Car.FUEL_TYPE;
import module.Motorcycle;
import module.Registrable;

public class Main {

    static class VehicleNameComparator implements Comparator<Vehicle> {
        @Override
        public int compare(Vehicle v1, Vehicle v2) {
            return v1.getName().compareToIgnoreCase(v2.getName());
        }
    }

    static class ManufacturerComparator implements Comparator<Vehicle> {
        @Override
        public int compare(Vehicle v1, Vehicle v2) {
            return v1.getManufacturer().compareToIgnoreCase(v2.getManufacturer());
        }
    }

    static class AnnualTaxComparator implements Comparator<Vehicle> {
        @Override
        public int compare(Vehicle v1, Vehicle v2) {
            return Double.compare(v2.calculateAnnualTax(), v1.calculateAnnualTax());
        }
    }

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
                    "1. Add a Car\n2. Add a Motorcycle\n3. Display All Vehicles\n4. Search for a Vehicle by ID\n5. Display All Electric Cars\n6. Display All Motorcycles with ABS\n7. Sort Vehicles by Value\n8. Sort Vehicles by Name\n9. Sort Vehicles by Manufacturer\n10. Sort Vehicles by Annual Tax\n11. Display Vehicle Tax Report\n12. Display Vehicle Statistics\n0. Exit\nEnter your choice: ");

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
                    System.out.print("Enter ID: ");
                    String ID = null;
                    while (true) {
                        try {
                            ID = br.readLine();
                            if (validID(vehicles, ID)) {
                                throw new DuplicateVehicleIdException("Vehicle with ID " + ID + " already exists");
                            }
                            break;
                        } catch (DuplicateVehicleIdException e) {
                            System.out.print(e.getMessage() + "\nRe-enter ID: ");
                        }
                    }

                    System.out.print("Enter name: ");
                    String name = br.readLine();

                    System.out.print("Enter manufacturer: ");
                    String manufacturer = br.readLine();

                    System.out.print("Enter value: ");
                    int value = 0;
                    while (true) {
                        try {
                            value = Integer.parseInt(br.readLine());
                            if (value <= 0) {
                                throw new InvalidVehicleValueException("Value must be greater than 0");
                            }
                            break;
                        } catch (NumberFormatException e) {
                            System.out.print("Invalid input\nRe-enter value: ");
                        } catch (InvalidVehicleValueException e) {
                            System.out.print(e.getMessage() + "\nRe-enter value: ");
                        }
                    }

                    System.out.print("Enter number of seat: ");
                    int numberOfSeat = 0;
                    while (true) {
                        try {
                            numberOfSeat = Integer.parseInt(br.readLine());
                            if (numberOfSeat <= 0) {
                                throw new InvalidVehicleValueException("Number of seat must be greater than 0");
                            }
                            break;
                        } catch (NumberFormatException e) {
                            System.out.print("Invalid input\nRe-enter number of seat: ");
                        } catch (InvalidVehicleValueException e) {
                            System.out.print(e.getMessage() + "\nRe-enter number of seat: ");
                        }
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

                    System.out.print("Enter insurance provider: ");
                    String insuranceProvider = br.readLine();

                    System.out.print("Enter coverage amount: ");
                    double coverageAmount = 0;
                    while (true) {
                        try {
                            coverageAmount = Double.parseDouble(br.readLine());
                            if (coverageAmount <= 0) {
                                throw new InvalidVehicleValueException("Coverage amount must be greater than 0");
                            }
                            break;
                        } catch (NumberFormatException e) {
                            System.out.print("Invalid input\nRe-enter coverage amount: ");
                        } catch (InvalidVehicleValueException e) {
                            System.out.print(e.getMessage() + "\nRe-enter coverage amount: ");
                        }
                    }

                    vehicles.add(new Car(ID, name, manufacturer, value, numberOfSeat, selectedFuel, insuranceProvider,
                            coverageAmount));
                    break;
                case 2:
                    System.out.print("Enter ID: ");
                    while (true) {
                        try {
                            ID = br.readLine();
                            if (validID(vehicles, ID)) {
                                throw new DuplicateVehicleIdException("Vehicle with ID " + ID + " already exists");
                            }
                            break;
                        } catch (DuplicateVehicleIdException e) {
                            System.out.print(e.getMessage() + "\nRe-enter ID: ");
                        }
                    }

                    System.out.print("Enter name: ");
                    name = br.readLine();

                    System.out.print("Enter manufacturer: ");
                    manufacturer = br.readLine();

                    System.out.print("Enter value: ");
                    while (true) {
                        try {
                            value = Integer.parseInt(br.readLine());
                            if (value <= 0) {
                                throw new InvalidVehicleValueException("Value must be greater than 0");
                            }
                            break;
                        } catch (NumberFormatException e) {
                            System.out.print("Invalid input\nRe-enter value: ");
                        } catch (InvalidVehicleValueException e) {
                            System.out.print(e.getMessage() + "\nRe-enter value: ");
                        }
                    }

                    System.out.print("Enter engine capacity: ");
                    double engineCapacity = 0;
                    while (true) {
                        try {
                            engineCapacity = Double.parseDouble(br.readLine());
                            if (engineCapacity <= 0) {
                                throw new InvalidVehicleValueException("Engine capacity must be greater than 0");
                            }
                            break;
                        } catch (NumberFormatException e) {
                            System.out.print("Invalid input\nRe-enter engine capacity: ");
                        } catch (InvalidVehicleValueException e) {
                            System.out.print(e.getMessage() + "\nRe-enter engine capacity: ");
                        }
                    }

                    System.out.print("Enter ABS support 1)Yes 2)No: ");
                    int ABSSupport = -1;
                    while (true) {
                        try {
                            ABSSupport = Integer.parseInt(br.readLine());
                            if (ABSSupport == 1 || ABSSupport == 2) {
                                break;
                            }
                            System.out.print("Invalid option\nRe-enter ABS support 1)Yes 2)No: ");
                        } catch (NumberFormatException e) {
                            System.out.print("Invalid input\nRe-enter ABS support 1)Yes 2)No: ");
                        }
                    }

                    System.out.print("Enter insurance provider: ");
                    insuranceProvider = br.readLine();

                    System.out.print("Enter coverage amount: ");
                    coverageAmount = 0;
                    while (true) {
                        try {
                            coverageAmount = Double.parseDouble(br.readLine());
                            if (coverageAmount <= 0) {
                                throw new InvalidVehicleValueException("Coverage amount must be greater than 0");
                            }
                            break;
                        } catch (NumberFormatException e) {
                            System.out.print("Invalid input\nRe-enter coverage amount: ");
                        } catch (InvalidVehicleValueException e) {
                            System.out.print(e.getMessage() + "\nRe-enter coverage amount: ");
                        }
                    }

                    vehicles.add(new Motorcycle(ID, name, manufacturer, value, engineCapacity, ABSSupport == 1,
                            insuranceProvider, coverageAmount));
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
                case 7:
                    Collections.sort(vehicles);
                    break;
                case 8:
                    vehicles.sort(new VehicleNameComparator());
                    break;
                case 9:
                    vehicles.sort(new ManufacturerComparator());
                    break;
                case 10:
                    vehicles.sort(new AnnualTaxComparator());
                    break;
                case 11:
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        for (Vehicle vehicle : vehicles) {
                            String vehicleType = (vehicle instanceof Car) ? "Car" : "Motorcycle";
                            double annualTax = vehicle.calculateAnnualTax();
                            String status = ((Registrable) vehicle).getRegistrationStatus();
                            System.out.println("Vehicle ID: " + vehicle.getID() + "\nVehicle Name: " + vehicle.getName()
                                    + "\nVehicle Type: " + vehicleType + "\nAnnual Tax: " + annualTax
                                    + "\nRegistration Status: " + status);
                        }
                    }
                    break;
                case 12:
                    System.out.println("Total Number of Vehicles: " + Vehicle.VehicleStatistics.getTotalVehicles()
                            + "\nTotal Number of Cars: " + Vehicle.VehicleStatistics.getTotalCars()
                            + "\nTotal Number of Motorcycles: " + Vehicle.VehicleStatistics.getTotalMotorcycles()
                            + "\nTotal Vehicle Value: " + Vehicle.VehicleStatistics.getTotalVehicleValue());
                    break;
                default:
                    System.out.println("Invalid choice");
                    break;
            }
        }
    }
}