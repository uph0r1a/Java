import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Ex7 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static abstract class PersonAbs {
        private String name;
        private int age;

        public PersonAbs(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public abstract void goToWork();
    }

    public static class Person extends PersonAbs {
        private String idNumber;

        public Person(String idNumber, String name, int age) {
            super(name, age);
            this.idNumber = idNumber;
        }

        public String getIdNumber() {
            return idNumber;
        }

        public void setIdNumber(String idNumber) {
            this.idNumber = idNumber;
        }

        @Override
        public void goToWork() {
            System.out.println("go to work");
        }
    }

    public static interface IHotel {
        public void addCustomer(Person guest, String roomType, int nights);

        public void deleteCustomer(String idNumber);

        public double calculatePrice(String idNumber);
    }

    public static record GuestRecord(Person guest, String roomType, int nights) {
    }

    public static class Hotel implements IHotel {
        private Map<String, GuestRecord> guests;

        public Hotel() {
            guests = new HashMap<>();
        }

        public Map<String, GuestRecord> getGuests() {
            return guests;
        }

        public void setGuests(Map<String, GuestRecord> guests) {
            this.guests = guests;
        }

        @Override
        public void addCustomer(Person guest, String roomType, int nights) {
            guests.put(guest.getIdNumber(), new GuestRecord(guest, roomType, nights));
        }

        @Override
        public void deleteCustomer(String idNumber) {
            guests.remove(idNumber);
        }

        @Override
        public double calculatePrice(String idNumber) {
            GuestRecord g = guests.get(idNumber);
            return g.nights()
                    * (g.roomType().equalsIgnoreCase("A") ? 500 : g.roomType().equalsIgnoreCase("B") ? 300 : 100);
        }
    }

    public static void main(String[] args) throws IOException {
        Hotel hotel = new Hotel();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    ===== HOTEL MANAGEMENT =====
                    1) Add guest
                    2) Remove guest
                    3) Calculate room charge
                    0) Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 3) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            switch (choice) {
                case 1 -> {
                    System.out.print("Enter guest id: ");
                    String id;
                    while (true) {
                        try {
                            id = br.readLine().strip();
                            if (!hotel.getGuests().containsKey(id)) {
                                break;
                            }
                            System.out.print("ID already exist\nRe-enter guest id: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter guest name: ");
                    String name = br.readLine().strip();

                    System.out.print("Enter guest age: ");
                    int age;
                    while (true) {
                        try {
                            age = Integer.parseInt(br.readLine());
                            if (age >= 0) {
                                break;
                            }
                            System.out.print("Invalid age\nRe-enter age: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter guest room type (A/B/C): ");
                    String type;
                    while (true) {
                        try {
                            type = br.readLine().strip().toUpperCase();
                            if (type.equals("A") || type.equals("B") || type.equals("C")) {
                                break;
                            }
                            System.out.print("Invalid type\nRe-enter guest room type (A/B/C): ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter number of night: ");
                    int night;
                    while (true) {
                        try {
                            night = Integer.parseInt(br.readLine());
                            if (night >= 0) {
                                break;
                            }
                            System.out.print("Invalid number of night\nRe-enter number of night: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    hotel.addCustomer(new Person(id, name, age), type, night);
                    System.out.println("Guest added successfully");
                }
                case 2 -> {
                    if (hotel.getGuests().isEmpty()) {
                        System.out.println("No guest yet");
                    } else {
                        System.out.print("Enter guest id: ");
                        String id;
                        while (true) {
                            try {
                                id = br.readLine().strip();
                                if (hotel.getGuests().containsKey(id)) {
                                    break;
                                }
                                System.out.print("No ID exist\nRe-enter guest id: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }

                        hotel.deleteCustomer(id);
                        System.out.println("Guest removed successfully");
                    }
                }
                case 3 -> {
                    if (hotel.getGuests().isEmpty()) {
                        System.out.println("No guest yet");
                    } else {
                        System.out.print("Enter guest id: ");
                        String id;
                        while (true) {
                            try {
                                id = br.readLine().strip();
                                if (hotel.getGuests().containsKey(id)) {
                                    break;
                                }
                                System.out.print("No ID exist\nRe-enter guest id: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }

                        double price = hotel.calculatePrice(id);
                        System.out.println("Room charge: $" + price);
                    }
                }
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}