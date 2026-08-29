import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Ex3 {
    public static class Product {
        private String productId, productName;
        private int stockQuantity;
        private double sellingPrice;

        public Product(String productId, String productName, int stockQuantity, double sellingPrice) {
            this.productId = productId;
            this.productName = productName;
            this.stockQuantity = stockQuantity;
            this.sellingPrice = sellingPrice;
        }

        public String getProductId() {
            return productId;
        }

        public String getProductName() {
            return productName;
        }

        public int getStockQuantity() {
            return stockQuantity;
        }

        public double getSellingPrice() {
            return sellingPrice;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public void setStockQuantity(int stockQuantity) {
            this.stockQuantity = stockQuantity;
        }

        public void setSellingPrice(double sellingPrice) {
            this.sellingPrice = sellingPrice;
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String, Product> storage = new HashMap<>();
        boolean isExit = false;
        while (!isExit) {
            System.out.print("""
                    1.  Receive stock
                    2.  Ship stock out
                    3.  Search for a product by name
                    4.  Print low-stock products
                    5.  Calculate total inventory value
                    0.  Exit
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
                    System.out.print("Enter product ID: ");
                    String productId = br.readLine().strip();
                    String productName;
                    double sellingPrice;

                    if (!storage.containsKey(productId)) {
                        System.out.print("Enter product name: ");
                        productName = br.readLine().strip();

                        System.out.print("Enter product price: ");
                        while (true) {
                            try {
                                sellingPrice = Double.parseDouble(br.readLine());
                                if (sellingPrice >= 0) {
                                    break;
                                }
                                System.out.print("Price cant be negative\nRe-enter price: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }
                    } else {
                        productName = storage.get(productId).getProductName();
                        sellingPrice = storage.get(productId).getSellingPrice();
                    }

                    System.out.print("Enter product stock to import: ");
                    int stockQuantity;
                    while (true) {
                        try {
                            stockQuantity = Integer.parseInt(br.readLine());
                            if (stockQuantity >= 0) {
                                break;
                            }
                            System.out.print("Stock cant be negative\nRe-enter stock: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    Product product = new Product(productId, productName,
                            storage.containsKey(productId) ? storage.get(productId).getStockQuantity() + stockQuantity
                                    : stockQuantity,
                            sellingPrice);
                    storage.put(product.getProductId(), product);
                }
                case 2 -> {
                    if (!storage.isEmpty()) {
                        System.out.print("Enter product ID: ");
                        String productId;
                        while (true) {
                            try {
                                productId = br.readLine().strip();
                                if (storage.containsKey(productId)) {
                                    break;
                                }
                                System.out.print("Invalid ID\nRe-enter product ID: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }

                        System.out.print("Enter product stock to export: ");
                        int stockQuantity;
                        while (true) {
                            try {
                                stockQuantity = Integer.parseInt(br.readLine());
                                if (stockQuantity >= 0
                                        && storage.get(productId).getStockQuantity() - stockQuantity >= 0) {
                                    break;
                                }
                                System.out.print("Not enough stock or invalid quantity\nRe-enter stock: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }

                        Product product = storage.get(productId);
                        product.setStockQuantity(product.getStockQuantity() - stockQuantity);
                    } else {
                        System.out.println("No product yet");
                    }
                }
                case 3 -> {
                    if (!storage.isEmpty()) {
                        System.out.print("Enter product name: ");
                        String productName = br.readLine().strip();
                        boolean found = false;

                        for (Product value : storage.values()) {
                            if (value.getProductName().toLowerCase().contains(productName.toLowerCase())) {
                                found = true;
                                System.out.println("Product ID: " + value.getProductId() + "\nProduct name: "
                                        + value.getProductName() + "\nProduct stock: " + value.getStockQuantity()
                                        + "\nProduct price: " + value.getSellingPrice() + "\n");
                            }
                        }

                        if (!found) {
                            System.out.println("No product matches \"" + productName + "\".\n");
                        }
                    } else {
                        System.out.println("No product yet");
                    }
                }
                case 4 -> {
                    if (!storage.isEmpty()) {
                        boolean found = false;

                        for (Product value : storage.values()) {
                            if (value.getStockQuantity() < 10) {
                                found = true;
                                System.out.println("Product ID: " + value.getProductId() + "\nProduct name: "
                                        + value.getProductName() + "\nProduct stock: " + value.getStockQuantity()
                                        + "\nProduct price: " + value.getSellingPrice());
                            }
                        }

                        if (!found) {
                            System.out.println("No product is low on stock.");
                        }
                    } else {
                        System.out.println("No product yet");
                    }
                }
                case 5 -> {
                    if (!storage.isEmpty()) {
                        System.out.println("Total product value: " + storage.values().stream()
                                .mapToDouble(p -> p.getSellingPrice() * p.getStockQuantity()).sum());
                    } else {
                        System.out.println("No product yet");
                    }
                }
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}