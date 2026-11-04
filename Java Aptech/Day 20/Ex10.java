
import java.io.*;
import java.util.*;

public class Ex10 {

    static class Hotel {
        private String hotelId;
        private String hotelName;
        private String address;
        private double roomPrice;
        private int starRating;

        public Hotel() {
        }

        public Hotel(String hotelId, String hotelName, String address,
                     double roomPrice, int starRating) {
            this.hotelId = hotelId;
            this.hotelName = hotelName;
            this.address = address;
            this.roomPrice = roomPrice;
            this.starRating = starRating;
        }

        public String getHotelId() {
            return hotelId;
        }

        public void setHotelId(String hotelId) {
            this.hotelId = hotelId;
        }

        public String getHotelName() {
            return hotelName;
        }

        public void setHotelName(String hotelName) {
            this.hotelName = hotelName;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public double getRoomPrice() {
            return roomPrice;
        }

        public void setRoomPrice(double roomPrice) {
            this.roomPrice = roomPrice;
        }

        public int getStarRating() {
            return starRating;
        }

        public void setStarRating(int starRating) {
            this.starRating = starRating;
        }

        @Override
        public String toString() {
            return String.format(
                "%-15s %-25s %-30s %-12.2f %d",
                hotelId, hotelName, address, roomPrice, starRating
            );
        }
    }

    static class Test {
        private static final BufferedReader br =
            new BufferedReader(new InputStreamReader(System.in));

        private static final String FILE_NAME = "hotel_data.txt";

        public static void main(String[] args) {
            List<Hotel> hotels = inputHotels();

            if (hotels == null) {
                return;
            }

            if (!saveHotels(hotels)) {
                return;
            }

            List<Hotel> loadedHotels = readHotels();

            if (loadedHotels == null) {
                return;
            }

            displayHotels(loadedHotels);
            searchHotels(loadedHotels);
        }

        // Input exactly 5 hotels
        public static List<Hotel> inputHotels() {
            List<Hotel> hotels = new ArrayList<>();

            System.out.println("Enter information for 5 hotels:");

            for (int i = 0; i < 5; i++) {
                System.out.println("\nHotel " + (i + 1));

                String hotelId;

                // Validate hotel ID and check duplicates before adding
                while (true) {
                    hotelId = readInput("Hotel ID: ");

                    if (!hotelId.matches("^\\d{3}-[A-Z]{3}-\\d{3}$")) {
                        System.out.println(
                            "Invalid hotel ID. Example: 123-ABC-456"
                        );
                        continue;
                    }

                    boolean duplicate = false;

                    for (Hotel hotel : hotels) {
                        if (hotel.getHotelId().equals(hotelId)) {
                            duplicate = true;
                            break;
                        }
                    }

                    if (duplicate) {
                        System.out.println(
                            "This hotel ID already exists. Enter another ID."
                        );
                        continue;
                    }

                    break;
                }

                // Validate hotel name
                String hotelName;

                while (true) {
                    hotelName = readInput("Hotel name: ");

                    if (hotelName.length() < 3) {
                        System.out.println(
                            "Hotel name must contain at least 3 characters."
                        );
                    } else {
                        break;
                    }
                }

                // Validate address
                String address;

                while (true) {
                    address = readInput("Address: ");

                    if (address.isEmpty()) {
                        System.out.println("Address cannot be empty.");
                    } else {
                        break;
                    }
                }

                // Validate room price
                double roomPrice;

                while (true) {
                    String input = readInput("Room price: ");

                    try {
                        roomPrice = Double.parseDouble(input);

                        if (!Double.isFinite(roomPrice) || roomPrice <= 0) {
                            System.out.println(
                                "Room price must be a number greater than 0."
                            );
                        } else {
                            break;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid room price. Try again.");
                    }
                }

                // Validate star rating
                int starRating;

                while (true) {
                    String input = readInput("Star rating (1-5): ");

                    try {
                        starRating = Integer.parseInt(input);

                        if (starRating < 1 || starRating > 5) {
                            System.out.println(
                                "Star rating must be between 1 and 5."
                            );
                        } else {
                            break;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println(
                            "Invalid star rating. Enter an integer."
                        );
                    }
                }

                // Add hotel only after all fields are valid
                hotels.add(new Hotel(
                    hotelId, hotelName, address, roomPrice, starRating
                ));

                System.out.println("Hotel added successfully.");
            }

            return hotels;
        }

        // Read keyboard input
        private static String readInput(String message) {
            try {
                System.out.print(message);
                String input = br.readLine();

                if (input == null) {
                    throw new EOFException("No more keyboard input.");
                }

                return input.trim();
            } catch (IOException e) {
                System.out.println("Error reading input: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }

        // Save hotels to a text file
        public static boolean saveHotels(List<Hotel> hotels) {
            try (BufferedWriter bw =
                     new BufferedWriter(new FileWriter(FILE_NAME))) {

                for (Hotel hotel : hotels) {
                    bw.write(
                        hotel.getHotelId() + "|" +
                        hotel.getHotelName() + "|" +
                        hotel.getAddress() + "|" +
                        hotel.getRoomPrice() + "|" +
                        hotel.getStarRating()
                    );

                    bw.newLine();
                }

                System.out.println("\nHotels saved successfully.");
                return true;

            } catch (IOException e) {
                System.out.println(
                    "Error saving hotel data: " + e.getMessage()
                );
                return false;
            }
        }

        // Read hotels from the text file
        public static List<Hotel> readHotels() {
            List<Hotel> hotels = new ArrayList<>();

            try (BufferedReader reader =
                     new BufferedReader(new FileReader(FILE_NAME))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|", -1);

                    if (parts.length != 5) {
                        System.out.println(
                            "Skipping invalid record: " + line
                        );
                        continue;
                    }

                    try {
                        String hotelId = parts[0];
                        String hotelName = parts[1];
                        String address = parts[2];
                        double roomPrice = Double.parseDouble(parts[3]);
                        int starRating = Integer.parseInt(parts[4]);

                        hotels.add(new Hotel(
                            hotelId, hotelName, address,
                            roomPrice, starRating
                        ));

                    } catch (NumberFormatException e) {
                        System.out.println(
                            "Skipping invalid record: " + line
                        );
                    }
                }

                return hotels;

            } catch (IOException e) {
                System.out.println(
                    "Error reading hotel data: " + e.getMessage()
                );
                return null;
            }
        }

        // Display all hotels
        public static void displayHotels(List<Hotel> hotels) {
            System.out.println("\n========== HOTEL LIST ==========");

            System.out.printf(
                "%-15s %-25s %-30s %-12s %s%n",
                "Hotel ID", "Hotel Name", "Address",
                "Room Price", "Stars"
            );

            for (Hotel hotel : hotels) {
                System.out.println(hotel);
            }
        }

        // Search hotels by name or address
        public static void searchHotels(List<Hotel> hotels) {
            System.out.println("\n========== HOTEL SEARCH ==========");
            System.out.println("1. Search by hotel name");
            System.out.println("2. Search by location (address)");

            int choice;

            while (true) {
                String input = readInput("Choose search option (1-2): ");

                try {
                    choice = Integer.parseInt(input);

                    if (choice == 1 || choice == 2) {
                        break;
                    }

                    System.out.println("Please choose 1 or 2.");

                } catch (NumberFormatException e) {
                    System.out.println("Invalid choice. Try again.");
                }
            }

            String keyword = readInput("Enter search keyword: ");
            boolean found = false;

            for (Hotel hotel : hotels) {
                boolean matches;

                if (choice == 1) {
                    matches = hotel.getHotelName()
                                   .toLowerCase()
                                   .contains(keyword.toLowerCase());
                } else {
                    matches = hotel.getAddress()
                                   .toLowerCase()
                                   .contains(keyword.toLowerCase());
                }

                if (matches) {
                    if (!found) {
                        System.out.println("\nSearch results:");
                        System.out.printf(
                            "%-15s %-25s %-30s %-12s %s%n",
                            "Hotel ID", "Hotel Name", "Address",
                            "Room Price", "Stars"
                        );
                    }

                    System.out.println(hotel);
                    found = true;
                }
            }

            if (!found) {
                System.out.println("NOT FOUND");
            }
        }
    }
}
