import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Ex2 {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static abstract class Vehicle implements Comparable<Vehicle> {
        private String ID, name, manufacturer;
        private int value;
        private final VehicleInsurance insurance;
        private LocalDate registrationDate;

        public Vehicle(String ID, String name, String manufacturer, int value, String insuranceProvider,
                double coverageAmount, LocalDate registrationDate) {
            this.ID = ID;
            this.name = name;
            this.manufacturer = manufacturer;
            this.value = value;
            this.insurance = new VehicleInsurance(insuranceProvider, coverageAmount);
            this.registrationDate = registrationDate;
        }

        public String getID() {
            return ID;
        }

        public void setID(String ID) {
            this.ID = ID;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getManufacturer() {
            return manufacturer;
        }

        public void setManufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public VehicleInsurance getInsurance() {
            return insurance;
        }

        public LocalDate getRegistrationDate() {
            return registrationDate;
        }

        public void setRegistrationDate(LocalDate registrationDate) {
            this.registrationDate = registrationDate;
        }

        public String displayInfo() {
            return "\nID: " + ID + "\nName: " + name + "\nManufacturer: " + manufacturer + "\nValue: " + value
                    + "\nInsurance Provider: " + insurance.getInsuranceProvider() + "\nCoverage Amount: "
                    + insurance.getCoverageAmount() + "\nRegistration Date: " + registrationDate;
        }

        @Override
        public int compareTo(Vehicle other) {
            return Integer.compare(this.value, other.value);
        }

        public abstract double calculateAnnualTax();

        public class VehicleInsurance {
            private final String insuranceProvider;
            private final double coverageAmount;

            public VehicleInsurance(String insuranceProvider, double coverageAmount) {
                this.insuranceProvider = insuranceProvider;
                this.coverageAmount = coverageAmount;
            }

            public String getInsuranceProvider() {
                return insuranceProvider;
            }

            public double getCoverageAmount() {
                return coverageAmount;
            }
        }

        public static class VehicleStatistics {
            private static int totalVehicles = 0, totalCars = 0, totalMotorcycles = 0;
            private static double totalVehicleValue = 0;

            public static int getTotalVehicles() {
                return totalVehicles;
            }

            public static int getTotalCars() {
                return totalCars;
            }

            public static int getTotalMotorcycles() {
                return totalMotorcycles;
            }

            public static double getTotalVehicleValue() {
                return totalVehicleValue;
            }

            public static void addCar(int value) {
                totalVehicles++;
                totalCars++;
                totalVehicleValue += value;
            }

            public static void addMotorcycle(int value) {
                totalVehicles++;
                totalMotorcycles++;
                totalVehicleValue += value;
            }
        }
    }

    public static interface Registrable {
        String getRegistrationStatus();
    }

    public static class Motorcycle extends Vehicle implements Registrable {
        private double engineCapacity;
        private boolean ABSSupported;

        public Motorcycle(String iD, String name, String manufacturer, int value, double engineCapacity,
                boolean ABSSupported, String insuranceProvider, double coverageAmount, LocalDate registrationDate) {
            super(iD, name, manufacturer, value, insuranceProvider, coverageAmount, registrationDate);
            this.engineCapacity = engineCapacity;
            this.ABSSupported = ABSSupported;
            VehicleStatistics.addMotorcycle(value);
        }

        public double getEngineCapacity() {
            return engineCapacity;
        }

        public void setEngineCapacity(double engineCapacity) {
            this.engineCapacity = engineCapacity;
        }

        public boolean isABSSupported() {
            return ABSSupported;
        }

        public void setABSSupported(boolean ABSSupported) {
            this.ABSSupported = ABSSupported;
        }

        @Override
        public String displayInfo() {
            return super.displayInfo() + "\nEngine capacity: " + engineCapacity + " cc\nABS supported: "
                    + (ABSSupported ? "Yes" : "No");
        }

        @Override
        public double calculateAnnualTax() {
            return getEngineCapacity() < 150 ? getValue() * 0.02 : getValue() * 0.04;
        }

        @Override
        public String getRegistrationStatus() {
            return ABSSupported ? "Safety Certified" : "Basic Certified";
        }
    }

    public static class InvalidVehicleValueException extends RuntimeException {
        public InvalidVehicleValueException(String message) {
            super(message);
        }
    }

    public static class DuplicateVehicleIdException extends RuntimeException {
        public DuplicateVehicleIdException(String message) {
            super(message);
        }
    }

    public static enum FuelType {
        Gasoline,
        Diesel,
        Electric
    }

    public static class Car extends Vehicle implements Registrable {
        private int numberOfSeats;
        private FuelType fuelType;

        public Car(String iD, String name, String manufacturer, int value, int numberOfSeats, FuelType fuelType,
                String insuranceProvider, double coverageAmount, LocalDate registrationDate) {
            super(iD, name, manufacturer, value, insuranceProvider, coverageAmount, registrationDate);
            this.numberOfSeats = numberOfSeats;
            this.fuelType = fuelType;
            VehicleStatistics.addCar(value);
        }

        public int getNumberOfSeats() {
            return numberOfSeats;
        }

        public void setNumberOfSeats(int numberOfSeats) {
            this.numberOfSeats = numberOfSeats;
        }

        public FuelType getFuelType() {
            return fuelType;
        }

        public void setFuelType(FuelType fuelType) {
            this.fuelType = fuelType;
        }

        @Override
        public String displayInfo() {
            return super.displayInfo() + "\nNumber of seat: " + numberOfSeats + "\nFuel type: " + fuelType;
        }

        @Override
        public double calculateAnnualTax() {
            String type = getFuelType().toString().toLowerCase();
            return switch (type) {
                case "gasoline" -> getValue() * 0.05;
                case "diesel" -> getValue() * 0.06;
                case "electric" -> getValue() * 0.03;
                default -> throw new UnsupportedOperationException("Unimplemented method 'calculateAnnualTax'");
            };
        }

        @Override
        public String getRegistrationStatus() {
            String type = getFuelType().toString().toLowerCase();
            if (type.equals("electric")) {
                return "Green Vehicle";
            } else if (type.equals("diesel") || type.equals("gasoline")) {
                return "Standard Vehicle";
            }
            throw new UnsupportedOperationException("Unimplemented method 'getRegistrationStatus'");
        }
    }

    public static boolean validID(List<Vehicle> vehicles, String ID) {
        return vehicles.stream().anyMatch(v -> v.getID().equals(ID));
    }

    public static boolean validFuelType(String type) {
        for (FuelType fuel : FuelType.values()) {
            if (fuel.name().equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    public static LocalDate readRegistrationDate(BufferedReader br) throws IOException {
        System.out.print("Enter registration date (yyyy-MM-dd): ");
        while (true) {
            String input = br.readLine();
            try {
                return LocalDate.parse(input, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                System.out.print(
                        "Invalid date format. Please enter the date using yyyy-MM-dd.\nRe-enter registration date: ");
            }
        }
    }

    public static void printVehicle(Vehicle vehicle) {
        System.out.println(vehicle.getClass().getSimpleName() + vehicle.displayInfo());
    }

    public static Optional<Vehicle> findVehicleByID(List<Vehicle> vehicles, String searchID) {
        return vehicles.stream().filter(v -> v.getID().equals(searchID)).findFirst();
    }

    public static Optional<Vehicle> findVehicleByName(List<Vehicle> vehicles, String searchName) {
        return vehicles.stream().filter(v -> v.getName().equalsIgnoreCase(searchName)).findFirst();
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<Vehicle> vehicles = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print(
                    "1. Add a Car\n2. Add a Motorcycle\n3. Display All Vehicles\n4. Search Vehicle by ID\n5. Search Vehicle by Name\n6. Display All Electric Cars\n7. Display All Motorcycles with ABS\n8. Display Vehicles by Manufacturer\n9. Sort Vehicles by Value\n10. Sort Vehicles by Name\n11. Sort Vehicles by Manufacturer\n12. Sort Vehicles by Annual Tax\n13. Display Vehicle Tax Report\n14. Display Vehicle Statistics\n15. Display Vehicles Registered Within the Last N Days\n0. Exit\nEnter your choice: ");

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

                    FuelType selectedFuel = null;
                    for (FuelType fuel : FuelType.values()) {
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

                    LocalDate carRegistrationDate = readRegistrationDate(br);
                    vehicles.add(new Car(ID, name, manufacturer, value, numberOfSeat, selectedFuel, insuranceProvider,
                            coverageAmount, carRegistrationDate));
                }
                case 2 -> {
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

                    LocalDate motoRegistrationDate = readRegistrationDate(br);
                    vehicles.add(new Motorcycle(ID, name, manufacturer, value, engineCapacity, ABSSupport == 1,
                            insuranceProvider, coverageAmount, motoRegistrationDate));
                }
                case 3 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        vehicles.forEach(v -> printVehicle(v));
                    }
                }
                case 4 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        System.out.print("Enter ID: ");
                        String searchID = br.readLine();

                        Optional<Vehicle> foundByID = findVehicleByID(vehicles, searchID);
                        foundByID.ifPresentOrElse(v -> printVehicle(v), () -> System.out.println("Vehicle not found"));
                    }
                }
                case 5 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        System.out.print("Enter name: ");
                        String searchName = br.readLine();

                        Optional<Vehicle> foundByName = findVehicleByName(vehicles, searchName);
                        foundByName.ifPresentOrElse(v -> printVehicle(v),
                                () -> System.out.println("Vehicle not found"));
                    }
                }
                case 6 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        List<Vehicle> electricCars = vehicles.stream().filter(v -> v instanceof Car)
                                .map(Car.class::cast).filter(car -> car.getFuelType() == FuelType.Electric)
                                .map(Vehicle.class::cast).toList();

                        if (electricCars.isEmpty()) {
                            System.out.println("No electric cars found");
                        } else {
                            electricCars.forEach(v -> printVehicle(v));
                        }
                    }
                }
                case 7 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        List<Vehicle> absMotorcycles = vehicles.stream().filter(v -> v instanceof Motorcycle)
                                .map(Motorcycle.class::cast).filter(Motorcycle::isABSSupported).map(Vehicle.class::cast)
                                .toList();

                        if (absMotorcycles.isEmpty()) {
                            System.out.println("No ABS-supported motorcycles found");
                        } else {
                            absMotorcycles.forEach(v -> printVehicle(v));
                        }
                    }
                }
                case 8 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        System.out.print("Enter manufacturer: ");
                        String searchMfr = br.readLine();

                        List<Vehicle> mfrResults = vehicles.stream()
                                .filter(v -> v.getManufacturer().equalsIgnoreCase(searchMfr)).toList();

                        if (mfrResults.isEmpty()) {
                            System.out.println("No vehicles found for manufacturer: " + searchMfr);
                        } else {
                            mfrResults.forEach(v -> printVehicle(v));
                        }
                    }
                }
                case 9 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        Collections.sort(vehicles);
                        System.out.println("Vehicles sorted by value (ascending):");
                        vehicles.forEach(v -> printVehicle(v));
                    }
                }
                case 10 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        vehicles.sort(Comparator.comparing(Vehicle::getName, String.CASE_INSENSITIVE_ORDER));
                        System.out.println("Vehicles sorted by name (A -> Z):");
                        vehicles.forEach(v -> printVehicle(v));
                    }
                }
                case 11 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        vehicles.sort(Comparator.comparing(Vehicle::getManufacturer, String.CASE_INSENSITIVE_ORDER));
                        System.out.println("Vehicles sorted by manufacturer (A -> Z):");
                        vehicles.forEach(v -> printVehicle(v));
                    }
                }
                case 12 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        vehicles.sort(Comparator.comparingDouble(Vehicle::calculateAnnualTax).reversed());
                        System.out.println("Vehicles sorted by annual tax (highest -> lowest):");
                        vehicles.forEach(v -> printVehicle(v));
                    }
                }
                case 13 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        for (Vehicle vehicle : vehicles) {
                            String vehicleType = vehicle.getClass().getSimpleName();
                            double annualTax = vehicle.calculateAnnualTax();
                            String status = ((Registrable) vehicle).getRegistrationStatus();
                            System.out.println("Vehicle ID: " + vehicle.getID() + "\nVehicle Name: " + vehicle.getName()
                                    + "\nVehicle Type: " + vehicleType + "\nAnnual Tax: " + annualTax
                                    + "\nRegistration Status: " + status);
                        }
                    }
                }
                case 14 ->
                    System.out.println("Total Number of Vehicles: " + Vehicle.VehicleStatistics.getTotalVehicles()
                            + "\nTotal Number of Cars: " + Vehicle.VehicleStatistics.getTotalCars()
                            + "\nTotal Number of Motorcycles: " + Vehicle.VehicleStatistics.getTotalMotorcycles()
                            + "\nTotal Vehicle Value: " + Vehicle.VehicleStatistics.getTotalVehicleValue());
                case 15 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle exist");
                    } else {
                        System.out.print("Enter number of days: ");
                        int days = -1;
                        while (true) {
                            try {
                                days = Integer.parseInt(br.readLine());
                                if (days < 0) {
                                    throw new InvalidVehicleValueException("Number of days must be 0 or greater");
                                }
                                break;
                            } catch (NumberFormatException e) {
                                System.out.print("Invalid input\nRe-enter number of days: ");
                            } catch (InvalidVehicleValueException e) {
                                System.out.print(e.getMessage() + "\nRe-enter number of days: ");
                            }
                        }

                        LocalDate today = LocalDate.now();
                        int finalDays = days;
                        List<Vehicle> recentResults = vehicles.stream()
                                .filter(v -> !v.getRegistrationDate().isBefore(today.minusDays(finalDays))
                                        && !v.getRegistrationDate().isAfter(today))
                                .toList();

                        if (recentResults.isEmpty()) {
                            System.out.println("No vehicles registered within the last " + days + " day(s)");
                        } else {
                            recentResults.forEach(v -> printVehicle(v));
                        }
                    }
                }
                default -> System.out.println("Invalid choice");
            }
        }
    }
}