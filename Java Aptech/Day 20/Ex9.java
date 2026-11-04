
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Ex9 {

    // Product class
    static class Product {
        private String productId;
        private String productName;
        private double price;
        private int quantity;
        private List<String> category;

        // Default constructor
        public Product() {
        }

        // Full-argument constructor
        public Product(String productId, String productName,
                       double price, int quantity,
                       List<String> category) {

            this.productId = productId;
            this.productName = productName;
            this.price = price;
            this.quantity = quantity;
            this.category = category;
        }

        // Getters
        public String getProductId() {
            return productId;
        }

        public String getProductName() {
            return productName;
        }

        public double getPrice() {
            return price;
        }

        public int getQuantity() {
            return quantity;
        }

        public List<String> getCategory() {
            return category;
        }

        // Setters
        public void setProductId(String productId) {
            this.productId = productId;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public void setCategory(List<String> category) {
            this.category = category;
        }

        @Override
        public String toString() {
            return "Product ID: " + productId
                    + ", Product Name: " + productName
                    + ", Price: " + price
                    + ", Quantity: " + quantity
                    + ", Category: " + String.join(", ", category);
        }
    }

    // Test class
    static class Test {

        private static final String FILE_NAME = "inventory_data.txt";

        // Read non-empty text
        private static String readNonBlank(
                BufferedReader br, String prompt) throws IOException {

            while (true) {
                System.out.print(prompt);

                String value = br.readLine();

                if (value == null) {
                    throw new EOFException("Input ended unexpectedly.");
                }

                if (!value.trim().isEmpty()) {
                    return value.trim();
                }

                System.out.println("This field cannot be blank.");
            }
        }

        // Read and validate product name
        private static String readProductName(BufferedReader br)
                throws IOException {

            while (true) {
                String name = readNonBlank(br, "Product name: ");

                if (name.length() >= 3) {
                    return name;
                }

                System.out.println(
                        "Product name must contain at least 3 characters."
                );
            }
        }

        // Read and validate price
        private static double readPrice(BufferedReader br)
                throws IOException {

            while (true) {
                System.out.print("Price: ");

                String input = br.readLine();

                if (input == null) {
                    throw new EOFException("Input ended unexpectedly.");
                }

                try {
                    double price = Double.parseDouble(input.trim());

                    if (Double.isFinite(price) && price > 0) {
                        return price;
                    }

                    System.out.println(
                            "Price must be greater than 0."
                    );

                } catch (NumberFormatException e) {
                    System.out.println(
                            "Please enter a valid decimal number."
                    );
                }
            }
        }

        // Read and validate quantity
        private static int readQuantity(BufferedReader br)
                throws IOException {

            while (true) {
                System.out.print("Quantity: ");

                String input = br.readLine();

                if (input == null) {
                    throw new EOFException("Input ended unexpectedly.");
                }

                try {
                    int quantity = Integer.parseInt(input.trim());

                    if (quantity >= 0) {
                        return quantity;
                    }

                    System.out.println(
                            "Quantity must be greater than or equal to 0."
                    );

                } catch (NumberFormatException e) {
                    System.out.println(
                            "Please enter a valid integer."
                    );
                }
            }
        }

        // Read categories separated by semicolons
        private static List<String> readCategories(BufferedReader br)
                throws IOException {

            while (true) {
                System.out.print(
                        "Categories (separated by semicolons): "
                );

                String input = br.readLine();

                if (input == null) {
                    throw new EOFException("Input ended unexpectedly.");
                }

                List<String> categories = new ArrayList<>();

                for (String item : input.split(";")) {
                    if (!item.trim().isEmpty()) {
                        categories.add(item.trim());
                    }
                }

                if (!categories.isEmpty()) {
                    return categories;
                }

                System.out.println(
                        "Please enter at least one category."
                );
            }
        }

        // Input exactly 5 products
        public static List<Product> inputProducts(BufferedReader br)
                throws IOException {

            List<Product> products = new ArrayList<>();

            for (int i = 0; i < 5; i++) {

                System.out.println(
                        "\n===== ENTER PRODUCT " + (i + 1) + " ====="
                );

                String id = readNonBlank(br, "Product ID: ");
                String name = readProductName(br);
                double price = readPrice(br);
                int quantity = readQuantity(br);
                List<String> categories = readCategories(br);

                products.add(
                        new Product(id, name, price,
                                quantity, categories)
                );
            }

            return products;
        }

        // Save products to inventory_data.txt
        public static void saveProducts(List<Product> products) {

            try (BufferedWriter bw = new BufferedWriter(
                    new FileWriter(FILE_NAME))) {

                for (Product product : products) {

                    String line = product.getProductId() + "|"
                            + product.getProductName() + "|"
                            + product.getPrice() + "|"
                            + product.getQuantity() + "|"
                            + String.join(";", product.getCategory());

                    bw.write(line);
                    bw.newLine();
                }

                System.out.println(
                        "\nProducts saved successfully to " + FILE_NAME
                );

            } catch (IOException e) {
                System.out.println(
                        "Error saving products: " + e.getMessage()
                );
            }
        }

        // Read products from inventory_data.txt
        public static List<Product> readProducts() {

            List<Product> products = new ArrayList<>();

            try (BufferedReader br = new BufferedReader(
                    new FileReader(FILE_NAME))) {

                String line;

                while ((line = br.readLine()) != null) {

                    String[] parts = line.split("\\|", -1);

                    if (parts.length != 5) {
                        System.out.println(
                                "Skipping invalid record: " + line
                        );
                        continue;
                    }

                    String id = parts[0].trim();
                    String name = parts[1].trim();
                    double price = Double.parseDouble(parts[2].trim());
                    int quantity = Integer.parseInt(parts[3].trim());

                    List<String> categories = new ArrayList<>();

                    for (String item : parts[4].split(";")) {
                        if (!item.trim().isEmpty()) {
                            categories.add(item.trim());
                        }
                    }

                    products.add(
                            new Product(id, name, price,
                                    quantity, categories)
                    );
                }

                System.out.println(
                        "\nProducts loaded successfully from " + FILE_NAME
                );

            } catch (IOException e) {
                System.out.println(
                        "Error reading products: " + e.getMessage()
                );

            } catch (NumberFormatException e) {
                System.out.println(
                        "Error: Invalid numeric data in the file."
                );
            }

            return products;
        }

        // Display all products
        public static void displayProducts(List<Product> products) {

            System.out.println("\n===== INVENTORY RECORDS =====");

            if (products.isEmpty()) {
                System.out.println("No products available.");
                return;
            }

            for (Product product : products) {
                System.out.println(product);
            }
        }

        // Search by product name or category
        public static void searchProducts(
                BufferedReader br, List<Product> products)
                throws IOException {

            System.out.println("\n===== SEARCH PRODUCTS =====");

            String keyword = readNonBlank(
                    br, "Enter product name or category: "
            );

            boolean found = false;

            for (Product product : products) {

                boolean nameMatches =
                        product.getProductName()
                                .toLowerCase()
                                .contains(keyword.toLowerCase());

                boolean categoryMatches = false;

                for (String category : product.getCategory()) {

                    if (category.toLowerCase()
                            .contains(keyword.toLowerCase())) {

                        categoryMatches = true;
                        break;
                    }
                }

                if (nameMatches || categoryMatches) {
                    System.out.println(product);
                    found = true;
                }
            }

            if (!found) {
                System.out.println("NOT FOUND");
            }
        }
    }

    // Main method
    public static void main(String[] args) {

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(System.in))) {

            // Step 1: Input five products
            List<Product> products = Test.inputProducts(br);

            // Step 2: Save products to file
            Test.saveProducts(products);

            // Step 3: Read products back from file
            List<Product> loadedProducts = Test.readProducts();

            // Step 4: Display all loaded products
            Test.displayProducts(loadedProducts);

            // Step 5: Search by name or category
            if (!loadedProducts.isEmpty()) {
                Test.searchProducts(br, loadedProducts);
            }

        } catch (IOException e) {
            System.out.println(
                    "Input error: " + e.getMessage()
            );
        }
    }
}
