import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Ex8 {
    public static class Product {
        private int id;
        private String name;
        private float price;

        public Product(int id, String name, float price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public float getPrice() {
            return price;
        }
    }

    public static class Booking {
        private int id, productID, quantity;
        private String productName, date;
        private float price;

        public Booking(int id, int productID, int quantity, String productName, String date, float price) {
            this.id = id;
            this.productID = productID;
            this.quantity = quantity;
            this.productName = productName.length() > 20 ? productName.substring(0, 20) : productName;
            this.date = date;
            this.price = price;
        }

        public int getId() {
            return id;
        }

        public int getProductID() {
            return productID;
        }

        public int getQuantity() {
            return quantity;
        }

        public String getProductName() {
            return productName;
        }

        public String getDate() {
            return date;
        }

        public float getPrice() {
            return price;
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<Product> products = new ArrayList<>();
        List<Booking> bookings = new ArrayList<>();
        int nextBookingId = 1;
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    Cafe Management Program
                    1. Enter Product | 2. Place Booking | 3. Find Booking by Date (ddmmyy)
                    4. Product List | 5. Booking History | 6. Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 1 && choice <= 6) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (choice) {
                case 1 -> {
                    System.out.print("Enter the total number of product: ");
                    int total;
                    while (true) {
                        try {
                            total = Integer.parseInt(br.readLine());
                            if (total > 0) {
                                break;
                            }
                            System.out.print("Invalid total number of product\nRe-enter the total number of product: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    for (int i = 0; i < total; i++) {
                        System.out.print("Enter product " + (i + 1) + " ID: ");
                        int id;
                        while (true) {
                            try {
                                id = Integer.parseInt(br.readLine());
                                final int searchID = id;
                                if (products.stream().noneMatch(p -> p.getId() == searchID)) {
                                    break;
                                }
                                System.out.print("ID exist\nRe-enter ID: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }

                        System.out.print("Enter product " + (i + 1) + " name: ");
                        String name;
                        while (true) {
                            name = br.readLine();
                            if (name.length() <= 20) {
                                break;
                            }
                            System.out.print(
                                    "Name too long (max 20 characters)\nRe-enter product " + (i + 1) + " name: ");
                        }

                        System.out.print("Enter product " + (i + 1) + " price: ");
                        float price;
                        while (true) {
                            try {
                                price = Float.parseFloat(br.readLine());
                                if (price >= 0 && price <= 100000) {
                                    break;
                                }
                                System.out.print("Invalid price\nRe-enter product " + (i + 1) + " price: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }
                        products.add(new Product(id, name, price));
                    }
                }
                case 2 -> {
                    if (products.isEmpty()) {
                        System.out.println("No products available. Please enter a product first.");
                        break;
                    }

                    Product product = null;
                    boolean found = false;

                    System.out.print("Enter product ID: ");
                    int id;
                    while (true) {
                        try {
                            id = Integer.parseInt(br.readLine());
                            for (Product p : products) {
                                if (p.getId() == id) {
                                    found = true;
                                    product = p;
                                    break;
                                }
                            }
                            if (found) {
                                break;
                            }
                            System.out.print("ID dont exist\nRe-enter ID: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter quantity: ");
                    int quantity;
                    while (true) {
                        try {
                            quantity = Integer.parseInt(br.readLine());
                            if (quantity >= 0) {
                                break;
                            }
                            System.out.print("Invalid quantity\nRe-enter quantity: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    bookings.add(new Booking(nextBookingId++, product.getId(), quantity, product.getName(),
                            LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")), product.getPrice()));
                    System.out.println("Booking placed successfully.");
                }
                case 3 -> {
                    if (bookings.isEmpty()) {
                        System.out.println("No bookings yet.");
                        break;
                    }

                    System.out.print("Enter a date (ddmmyy): ");
                    String date;
                    while (true) {
                        try {
                            date = br.readLine();
                            LocalDate.parse(date, DateTimeFormatter.ofPattern("ddMMyy"));
                            break;
                        } catch (Exception e) {
                            System.out.print("Error: " + e.getMessage() + "\nRe-enter a date: ");
                        }
                    }

                    boolean found = false;
                    for (Booking booking : bookings) {
                        if (booking.getDate().equals(date)) {
                            System.out.println("ID: " + booking.getId() + "\nProduct ID: " + booking.getProductID()
                                    + "\nProduct name: " + booking.getProductName() + "\nPrice: " + booking.getPrice()
                                    + "\nDate: " + booking.getDate() + "\nQuantity: " + booking.getQuantity());
                            found = true;
                        }
                    }
                    if (!found) {
                        System.out.println("No bookings found for that date.");
                    }
                }
                case 4 -> {
                    if (products.isEmpty()) {
                        System.out.println("No products available.");
                        break;
                    }
                    products.stream().sorted(Comparator.comparing(Product::getName).thenComparing(Product::getPrice))
                            .forEach(product -> System.out.println("ID: " + product.getId() + "\nName: "
                                    + product.getName() + "\nPrice: " + product.getPrice() + "\n"));
                }
                case 5 -> {
                    if (bookings.isEmpty()) {
                        System.out.println("No bookings yet.");
                        break;
                    }
                    for (Booking booking : bookings) {
                        System.out.println("ID: " + booking.getId() + "\nProduct ID: " + booking.getProductID()
                                + "\nProduct name: " + booking.getProductName() + "\nPrice: " + booking.getPrice()
                                + "\nDate: " + booking.getDate() + "\nQuantity: " + booking.getQuantity());
                    }
                }
                case 6 -> isExit = true;
                default -> System.out.println("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}