import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Ex5 {
    public static class Product {
        private int id, quantity;
        private String name;
        private float price;

        public Product(int id, int quantity, String name, float price) {
            this.id = id;
            this.quantity = quantity;
            this.name = name;
            this.price = price;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public float getPrice() {
            return price;
        }

        public void setPrice(float price) {
            this.price = price;
        }
    }

    public static class Order {
        private int id, quantity;
        private LocalDateTime date;

        public Order(int id, int quantity, LocalDateTime date) {
            this.id = id;
            this.quantity = quantity;
            this.date = date;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public LocalDateTime getDate() {
            return date;
        }

        public void setDate(LocalDateTime date) {
            this.date = date;
        }
    }

    public static boolean idExists(List<Product> products, int id) {
        return products.stream().anyMatch(p -> p.getId() == id);
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<Product> products = new ArrayList<>();
        List<Order> orders = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    Product Management Program
                    1. Enter Data
                    2. Low-Stock Report
                    3. Sort by Price
                    4. Sell Product
                    5. Sales Report
                    6. Exit
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
                    System.out.print("Enter the total number of products: ");
                    int totalNumber;
                    while (true) {
                        try {
                            totalNumber = Integer.parseInt(br.readLine());
                            if (totalNumber > 0) {
                                break;
                            }
                            System.out.println(
                                    "Total number of products cant be negative\nRe-enter the total number of products: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.println("Enter id: ");
                    int id;
                    while (true) {
                        try {
                            id = Integer.parseInt(br.readLine());
                            if (!idExists(products, id)) {
                                break;
                            }
                            System.out.println("ID already exist\nRe-enter id: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.println("Enter name: ");
                    String name = br.readLine();

                    System.out.println("Enter price: ");
                    float price;
                    while (true) {
                        try {
                            price = Float.parseFloat(br.readLine());
                            if (price >= 0 && price <= 10000000) {
                                break;
                            }
                            System.out.println("Invalid price\nRe-enter price: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.println("Enter quantity: ");
                    int quantity;
                    while (true) {
                        try {
                            quantity = Integer.parseInt(br.readLine());
                            if (quantity >= 0) {
                                break;
                            }
                            System.out.println("Invalid quantity\nRe-enter quantity: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    products.add(new Product(id, quantity, name, price));
                }
                case 2 -> {
                    System.out.println("The 3 products with the lowest stock quantity: " + products.stream()
                            .sorted(Comparator.comparingInt(Product::getQuantity)).toList().subList(0, 3));
                }
                case 3 -> {
                }
                case 4 -> {
                }
                case 5 -> {
                }
                case 6 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}
