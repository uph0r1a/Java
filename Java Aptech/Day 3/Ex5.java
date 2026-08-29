import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Ex5 {
    private static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

        @Override
        public String toString() {
            return "ID: " + id + " | Name: " + name + " | Price: $" + String.format("%.2f", price) + " | Quantity: "
                    + quantity;
        }
    }

    public static class Order {
        private int productId, quantity;
        private LocalDateTime date;

        public Order(int productId, int quantity, LocalDateTime date) {
            this.productId = productId;
            this.quantity = quantity;
            this.date = date;
        }

        public int getProductId() {
            return productId;
        }

        public void setProductId(int productId) {
            this.productId = productId;
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

    public static Optional<Product> findProductById(List<Product> products, int id) {
        return products.stream().filter(p -> p.getId() == id).findFirst();
    }

    private static int readInt(String prompt) throws IOException {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(br.readLine().trim());
            } catch (Exception e) {
                System.out.println("Invalid number, please try again.");
            }
        }
    }

    private static int readIntWithCondition(String prompt, java.util.function.IntPredicate condition, String errorMsg)
            throws IOException {
        while (true) {
            int value = readInt(prompt);
            if (condition.test(value)) {
                return value;
            }
            System.out.println(errorMsg);
        }
    }

    private static float readFloatWithCondition(String prompt, java.util.function.DoublePredicate condition,
            String errorMsg) throws IOException {
        while (true) {
            System.out.print(prompt);
            try {
                float value = Float.parseFloat(br.readLine().trim());
                if (condition.test(value)) {
                    return value;
                }
                System.out.println(errorMsg);
            } catch (Exception e) {
                System.out.println("Invalid number, please try again.");
            }
        }
    }

    private static void enterData(List<Product> products) throws IOException {
        int totalNumber = readIntWithCondition("Enter the total number of products: ", n -> n > 0,
                "Total number of products can't be zero or negative.");

        for (int i = 0; i < totalNumber; i++) {
            System.out.println("\nProduct " + (i + 1) + " of " + totalNumber + ":");
            int id = readIntWithCondition("Enter id: ", val -> !idExists(products, val),
                    "ID already exists, please enter a different one.");

            System.out.print("Enter name: ");
            String name = br.readLine().trim();
            float price = readFloatWithCondition("Enter price: ", p -> p >= 0 && p <= 10_000_000,
                    "Invalid price, must be between 0 and 10,000,000.");
            int quantity = readIntWithCondition("Enter quantity: ", q -> q >= 0,
                    "Invalid quantity, must not be negative.");

            products.add(new Product(id, quantity, name, price));
        }
        System.out.println("Products entered successfully!");
    }

    private static void lowStockReport(List<Product> products) {
        if (products.isEmpty()) {
            System.out.println("No products available.");
            return;
        }

        List<Product> lowest = products.stream().sorted(Comparator.comparingInt(Product::getQuantity)).toList();

        int limit = Math.min(3, lowest.size());
        System.out.println("The " + limit + " product(s) with the lowest stock quantity:");
        for (Product p : lowest.subList(0, limit)) {
            System.out.println(p);
        }
    }

    private static void sortByPrice(List<Product> products) {
        if (products.isEmpty()) {
            System.out.println("No products available.");
            return;
        }

        System.out.println("Products sorted by price (highest to lowest):");
        products.stream().sorted(Comparator.comparing(Product::getPrice).reversed()).forEach(System.out::println);
    }

    private static void sellProduct(List<Product> products, List<Order> orders) throws IOException {
        if (products.isEmpty()) {
            System.out.println("No products available to sell.");
            return;
        }

        System.out.println("Current products:");
        products.forEach(System.out::println);

        int id;
        Product product;
        while (true) {
            id = readInt("\nEnter the product id to sell: ");
            Optional<Product> found = findProductById(products, id);
            if (found.isPresent()) {
                product = found.get();
                break;
            }
            System.out.println("No product found with that id, please try again.");
        }

        final Product selected = product;
        int quantity = readIntWithCondition("Enter quantity to sell: ", q -> q >= 0 && q <= selected.getQuantity(),
                "Invalid quantity, must be between 0 and the current stock (" + selected.getQuantity() + ").");

        product.setQuantity(product.getQuantity() - quantity);
        orders.add(new Order(product.getId(), quantity, LocalDateTime.now()));

        System.out.println("Sale recorded! Remaining stock for " + product.getName() + ": " + product.getQuantity());
    }

    private static void salesReport(List<Product> products, List<Order> orders) {
        if (orders.isEmpty()) {
            System.out.println("No sales recorded yet.");
            return;
        }

        System.out.println("Sales report:");
        for (Order order : orders) {
            String productName = findProductById(products, order.getProductId()).map(Product::getName)
                    .orElse("Unknown product");

            System.out.println("Product ID: " + order.getProductId() + " | Name: " + productName + " | Quantity sold: "
                    + order.getQuantity() + " | Date: " + order.getDate().format(DATE_FORMAT));
        }
    }

    public static void main(String[] args) throws IOException {
        List<Product> products = new ArrayList<>();
        List<Order> orders = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    Product Management Program
                    1. Enter Data | 2. Low-Stock Report | 3. Sort by Price
                    4. Sell Product | 5. Sales Report | 6. Exit
                    Enter your choice:\s""");

            int choice = readIntWithCondition("", c -> c >= 1 && c <= 6, "Invalid choice, please re-enter.");

            switch (choice) {
                case 1 -> enterData(products);
                case 2 -> lowStockReport(products);
                case 3 -> sortByPrice(products);
                case 4 -> sellProduct(products, orders);
                case 5 -> salesReport(products, orders);
                case 6 -> isExit = true;
                default -> System.out.println("Invalid choice, please re-enter.");
            }
        }
    }
}