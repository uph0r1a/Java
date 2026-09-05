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
                    choice = Integer.parseInt(br.readLine().strip());
                    if (choice >= 0 && choice <= 5) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter your choice: ");
                }
            }

            switch (choice) {
                case 1 -> {
                    System.out.print("Enter product ID: ");
                    String productId = br.readLine().strip();
                    Product existing = storage.get(productId);

                    String productName;
                    double sellingPrice;

                    if (existing == null) {
                        System.out.print("Enter product name: ");
                        productName = br.readLine().strip();

                        System.out.print("Enter product price: ");
                        while (true) {
                            try {
                                sellingPrice = Double.parseDouble(br.readLine().strip());
                                if (sellingPrice >= 0) {
                                    break;
                                }
                                System.out.print("Price cant be negative\nRe-enter price: ");
                            } catch (Exception e) {
                                System.out.print("Error: " + e.getMessage() + "\nRe-enter price: ");
                            }
                        }
                    } else {
                        productName = existing.getProductName();
                        sellingPrice = existing.getSellingPrice();
                    }

                    System.out.print("Enter product stock to import: ");
                    int stockQuantity;
                    while (true) {
                        try {
                            stockQuantity = Integer.parseInt(br.readLine().strip());
                            if (stockQuantity > 0) {
                                break;
                            }
                            System.out.print("Stock must be greater than 0\nRe-enter stock: ");
                        } catch (Exception e) {
                            System.out.print("Error: " + e.getMessage() + "\nRe-enter stock: ");
                        }
                    }

                    if (existing == null) {
                        Product product = new Product(productId, productName, stockQuantity, sellingPrice);
                        storage.put(product.getProductId(), product);
                    } else {
                        existing.setStockQuantity(existing.getStockQuantity() + stockQuantity);
                    }
                    System.out.println("Stock received successfully.");
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
                                System.out.print("Error: " + e.getMessage() + "\nRe-enter product ID: ");
                            }
                        }

                        Product product = storage.get(productId);

                        System.out.print("Enter product stock to export: ");
                        int stockQuantity;
                        while (true) {
                            try {
                                stockQuantity = Integer.parseInt(br.readLine().strip());
                                if (stockQuantity <= 0) {
                                    System.out.print("Quantity must be greater than 0\nRe-enter stock: ");
                                } else if (stockQuantity > product.getStockQuantity()) {
                                    System.out.print("Not enough stock available\nRe-enter stock: ");
                                } else {
                                    break;
                                }
                            } catch (Exception e) {
                                System.out.print("Error: " + e.getMessage() + "\nRe-enter stock: ");
                            }
                        }

                        product.setStockQuantity(product.getStockQuantity() - stockQuantity);
                        System.out.println("Stock shipped successfully.");
                    } else {
                        System.out.println("No product yet");
                    }
                }
                case 3 -> {
                    if (!storage.isEmpty()) {
                        System.out.print("Enter product name: ");
                        String productName = br.readLine().strip();
                        boolean found = false;

                        for (Product product : storage.values()) {
                            if (product.getProductName().toLowerCase().contains(productName.toLowerCase())) {
                                found = true;
                                System.out.println("Product ID: " + product.getProductId() + "\nProduct name: "
                                        + product.getProductName() + "\nProduct stock: " + product.getStockQuantity()
                                        + "\nProduct price: " + product.getSellingPrice());
                            }
                        }

                        if (!found) {
                            System.out.println("No product matches \"" + productName + "\".");
                        }
                    } else {
                        System.out.println("No product yet");
                    }
                }
                case 4 -> {
                    if (!storage.isEmpty()) {
                        boolean found = false;

                        for (Product product : storage.values()) {
                            if (product.getStockQuantity() < 10) {
                                found = true;
                                System.out.println("Product ID: " + product.getProductId() + "\nProduct name: "
                                        + product.getProductName() + "\nProduct stock: " + product.getStockQuantity()
                                        + "\nProduct price: " + product.getSellingPrice());
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