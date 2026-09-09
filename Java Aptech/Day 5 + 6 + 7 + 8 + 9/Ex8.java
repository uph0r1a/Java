import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Ex8 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static interface IVehicle {
        public void startEngine();

        public void stopEngine();

        public void brake();
    }

    public static abstract class Vehicle implements IVehicle {
        private String id, manufacturer, color;
        private int yearOfManufacture;
        private double sellingPrice;

        public Vehicle(String manufacturer, String color, int yearOfManufacture, double sellingPrice) {
            this.id = UUID.randomUUID().toString();
            this.manufacturer = manufacturer;
            this.color = color;
            this.yearOfManufacture = yearOfManufacture;
            this.sellingPrice = sellingPrice;
        }

        public String getId() {
            return id;
        }

        public String getManufacturer() {
            return manufacturer;
        }

        public void setManufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public int getYearOfManufacture() {
            return yearOfManufacture;
        }

        public void setYearOfManufacture(int yearOfManufacture) {
            this.yearOfManufacture = yearOfManufacture;
        }

        public double getSellingPrice() {
            return sellingPrice;
        }

        public void setSellingPrice(double sellingPrice) {
            this.sellingPrice = sellingPrice;
        }

        @Override
        public abstract void startEngine();

        @Override
        public void stopEngine() {
            System.out.println("Stopping engine");
        }

        @Override
        public void brake() {
            System.out.println("Braking");
        }

        @Override
        public String toString() {
            return "\nID: " + id + "\nManufacturer: " + manufacturer + "\nColor: " + color + "\nYear of manufacture: "
                    + yearOfManufacture + "\nSelling price: " + sellingPrice;
        }
    }

    public static class Car extends Vehicle {
        private int numberOfSeats;
        private String engineType;

        public Car(String manufacturer, String color, int yearOfManufacture, double sellingPrice, int numberOfSeats,
                String engineType) {
            super(manufacturer, color, yearOfManufacture, sellingPrice);
            this.numberOfSeats = numberOfSeats;
            this.engineType = engineType;
        }

        public int getNumberOfSeats() {
            return numberOfSeats;
        }

        public void setNumberOfSeats(int numberOfSeats) {
            this.numberOfSeats = numberOfSeats;
        }

        public String getEngineType() {
            return engineType;
        }

        public void setEngineType(String engineType) {
            this.engineType = engineType;
        }

        @Override
        public void startEngine() {
            System.out.println("Starting car engine");
        }

        public int boardPassengers(int numberOfPassengers) {
            if (numberOfPassengers > numberOfSeats) {
                int excess = numberOfPassengers - numberOfSeats;
                System.out.println(excess + " passengers do not fit");
                return excess;
            }
            System.out.println("Everyone fits");
            return 0;
        }

        @Override
        public String toString() {
            return super.toString() + "\nNumber of seats: " + numberOfSeats + "\nEngine type: " + engineType;
        }
    }

    public static class Motorcycle extends Vehicle {
        private int enginePower;

        public Motorcycle(String manufacturer, String color, int yearOfManufacture, double sellingPrice,
                int enginePower) {
            super(manufacturer, color, yearOfManufacture, sellingPrice);
            this.enginePower = enginePower;
        }

        public int getEnginePower() {
            return enginePower;
        }

        public void setEnginePower(int enginePower) {
            this.enginePower = enginePower;
        }

        @Override
        public void startEngine() {
            System.out.println("Starting motorcycle engine");
        }

        @Override
        public String toString() {
            return super.toString() + "\nEngine power: " + enginePower;
        }
    }

    public static class Truck extends Vehicle {
        private int loadCapacity;

        public Truck(String manufacturer, String color, int yearOfManufacture, double sellingPrice, int loadCapacity) {
            super(manufacturer, color, yearOfManufacture, sellingPrice);
            this.loadCapacity = loadCapacity;
        }

        public int getLoadCapacity() {
            return loadCapacity;
        }

        public void setLoadCapacity(int loadCapacity) {
            this.loadCapacity = loadCapacity;
        }

        @Override
        public void startEngine() {
            System.out.println("Starting truck engine");
        }

        public int loadCargo(int weight) {
            if (weight > loadCapacity) {
                System.out.println("Load weight is not suitable");
                return weight - loadCapacity;
            }
            System.out.println("Load weight is suitable");
            return 0;
        }

        @Override
        public String toString() {
            return super.toString() + "\nLoad capacity: " + loadCapacity;
        }
    }

    public static void main(String[] args) throws IOException {
        List<Vehicle> vehicles = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    ===== TRANSPORTATION MANAGEMENT =====
                    1) Add vehicle
                    2) Load truck cargo
                    3) Board car passenger
                    4) Search vehicles
                    5) Remove vehicle
                    0) Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 5) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (choice) {
                case 1 -> {
                    System.out.print("Enter vehicle type (Car/Motorcycle/Truck): ");
                    String type;
                    while (true) {
                        try {
                            type = br.readLine().strip().toLowerCase();
                            if (type.equalsIgnoreCase("Car") || type.equalsIgnoreCase("Motorcycle")
                                    || type.equalsIgnoreCase("Truck")) {
                                break;
                            }
                            System.out.print("Invalid type\nRe-enter vehicle type (Car/Motorcycle/Truck): ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter vehicle manufacturer: ");
                    String manufacturer = br.readLine().strip();

                    System.out.print("Enter year of manufacture: ");
                    int year;
                    while (true) {
                        try {
                            year = Integer.parseInt(br.readLine());
                            if (year >= 1900 && year <= LocalDate.now().getYear()) {
                                break;
                            }
                            System.out.print("Invalid year\nRe-enter year of manufacture: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter selling price: ");
                    double price;
                    while (true) {
                        try {
                            price = Double.parseDouble(br.readLine());
                            if (price >= 0) {
                                break;
                            }
                            System.out.print("Invalid price\nRe-enter selling price: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter vehicle color: ");
                    String color = br.readLine().strip();

                    switch (type) {
                        case "car" -> {
                            System.out.print("Enter number of seat: ");
                            int seat;
                            while (true) {
                                try {
                                    seat = Integer.parseInt(br.readLine());
                                    if (seat >= 0) {
                                        break;
                                    }
                                    System.out.print("Invalid number of seat\nRe-enter number of seat: ");
                                } catch (Exception e) {
                                    System.out.println("Error: " + e.getMessage());
                                }
                            }

                            System.out.print("Enter engine type: ");
                            String engine = br.readLine().strip();

                            vehicles.add(new Car(manufacturer, color, year, price, seat, engine));
                            System.out.println("Car added successfully");
                        }
                        case "motorcycle" -> {
                            System.out.print("Enter engine power: ");
                            int power;
                            while (true) {
                                try {
                                    power = Integer.parseInt(br.readLine());
                                    if (power >= 0) {
                                        break;
                                    }
                                    System.out.print("Invalid engine power\nRe-enter engine power: ");
                                } catch (Exception e) {
                                    System.out.println("Error: " + e.getMessage());
                                }
                            }

                            vehicles.add(new Motorcycle(manufacturer, color, year, price, power));
                            System.out.println("Motorcycle added successfully");
                        }
                        case "truck" -> {
                            System.out.print("Enter load capacity: ");
                            int capacity;
                            while (true) {
                                try {
                                    capacity = Integer.parseInt(br.readLine());
                                    if (capacity >= 0) {
                                        break;
                                    }
                                    System.out.print("Invalid load capacity\nRe-enter load capacity: ");
                                } catch (Exception e) {
                                    System.out.println("Error: " + e.getMessage());
                                }
                            }

                            vehicles.add(new Truck(manufacturer, color, year, price, capacity));
                            System.out.println("Truck added successfully");
                        }
                    }
                }
                case 2 -> {
                    if (vehicles.isEmpty() || vehicles.stream().noneMatch(v -> v instanceof Truck)) {
                        System.out.println("No vehicle yet");
                    } else {
                        System.out.print("Enter vehicle ID: ");
                        String id = br.readLine().strip();
                        Vehicle v = vehicles.stream().filter(x -> Objects.equals(x.getId(), id)).findFirst()
                                .orElse(null);
                        if (v == null) {
                            System.out.println("No vehicle found with that ID");
                        } else if (!(v instanceof Truck t)) {
                            System.out.println("The ID belongs to a Car or Motorcycle");
                        } else {
                            System.out.print("Enter cargo weight: ");
                            int weight;
                            while (true) {
                                try {
                                    weight = Integer.parseInt(br.readLine());
                                    if (weight >= 0) {
                                        break;
                                    }
                                    System.out.print("Invalid weight\nRe-enter cargo weight: ");
                                } catch (Exception e) {
                                    System.out.println("Error: " + e.getMessage());
                                }
                            }

                            t.loadCargo(weight);
                        }
                    }
                }
                case 3 -> {
                    if (vehicles.isEmpty() || vehicles.stream().noneMatch(v -> v instanceof Car)) {
                        System.out.println("No vehicle yet");
                    } else {
                        System.out.print("Enter vehicle ID: ");
                        String id = br.readLine().strip();
                        Vehicle v = vehicles.stream().filter(x -> Objects.equals(x.getId(), id)).findFirst()
                                .orElse(null);
                        if (v == null) {
                            System.out.println("No vehicle found with that ID");
                        } else if (!(v instanceof Car c)) {
                            System.out.println("The ID belongs to a Truck or Motorcycle");
                        } else {
                            System.out.print("Enter number of passenger board: ");
                            int passenger;
                            while (true) {
                                try {
                                    passenger = Integer.parseInt(br.readLine());
                                    if (passenger >= 0) {
                                        break;
                                    }
                                    System.out.print(
                                            "Invalid number of passenger board\nRe-enter number of passenger board: ");
                                } catch (Exception e) {
                                    System.out.println("Error: " + e.getMessage());
                                }
                            }

                            c.boardPassengers(passenger);
                        }
                    }
                }
                case 4 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle yet");
                    } else {
                        System.out.print("Enter vehicles manufacturer or color: ");
                        String search = br.readLine().strip();

                        List<Vehicle> result = vehicles.stream().filter(v -> Objects.equals(v.getManufacturer(), search)
                                || Objects.equals(v.getColor(), search)).toList();

                        if (result.isEmpty()) {
                            System.out.println("No vehicle found");
                        } else {
                            result.forEach(System.out::println);
                        }
                    }
                }
                case 5 -> {
                    if (vehicles.isEmpty()) {
                        System.out.println("No vehicle yet");
                    } else {
                        System.out.print("Enter vehicle ID: ");
                        String id = br.readLine().strip();

                        boolean removed = vehicles.removeIf(v -> Objects.equals(v.getId(), id));
                        if (removed) {
                            System.out.println("Vehicle removed successfully");
                        } else {
                            System.out.println("Vehicle doesn't exist");
                        }
                    }
                }
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}