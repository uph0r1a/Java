import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class Ex4 {
    public static class Product {
        private String productId, productName;
        private double sellingPrice;

        public Product(String productId, String productName, double sellingPrice) {
            this.productId = productId;
            this.productName = productName;
            this.sellingPrice = sellingPrice;
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public double getSellingPrice() {
            return sellingPrice;
        }

        public void setSellingPrice(double sellingPrice) {
            this.sellingPrice = sellingPrice;
        }
    }

    public static class CartItem {
        private Product product;
        private int quantity;

        public CartItem(Product product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }

        public Product getProduct() {
            return product;
        }

        public void setProduct(Product product) {
            this.product = product;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String, CartItem> items = new HashMap<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    1.  Add Item
                    2.  Remove item
                    3.  Total cost
                    4.  Cart detail
                    0.  Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 4) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter choice: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter choice: ");
                }
            }

            switch (choice) {
                case 1 -> {
                    System.out.print("Enter product ID: ");
                    String id = br.readLine();

                    Product product;
                    if (items.containsKey(id)) {
                        product = items.get(id).getProduct();
                    } else {
                        System.out.print("Enter item name: ");
                        String name = br.readLine();

                        double price;
                        while (true) {
                            System.out.print("Enter selling price: ");
                            try {
                                price = Double.parseDouble(br.readLine());
                                if (price >= 0) {
                                    break;
                                }
                                System.out.println("Price can't be negative.");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }

                        product = new Product(id, name, price);
                    }

                    System.out.print("Enter item stock to add: ");
                    int stock;
                    while (true) {
                        try {
                            stock = Integer.parseInt(br.readLine());
                            if (stock >= 0) {
                                break;
                            }
                            System.out.print("Stock cant be negative\nRe-enter stock to add: ");
                        } catch (Exception e) {
                            System.out.print("Error: " + e.getMessage() + "\nRe-enter stock to add: ");
                        }
                    }

                    int newQuantity = items.containsKey(id) ? items.get(id).getQuantity() + stock : stock;
                    items.put(id, new CartItem(product, newQuantity));
                }
                case 2 -> {
                    if (!items.isEmpty()) {
                        System.out.print("Enter product ID: ");
                        String id;
                        while (true) {
                            try {
                                id = br.readLine();
                                if (items.containsKey(id)) {
                                    break;
                                }
                                System.out.print("Product ID doesn't exist\nRe-enter product ID: ");
                            } catch (Exception e) {
                                System.out.print("Error: " + e.getMessage() + "\nRe-enter product ID: ");
                            }
                        }

                        System.out.print("Enter item stock to remove: ");
                        int stock;
                        while (true) {
                            try {
                                stock = Integer.parseInt(br.readLine());
                                if (stock >= 0) {
                                    break;
                                }
                                System.out.print("Stock cant be negative\nRe-enter stock to remove: ");
                            } catch (Exception e) {
                                System.out.print("Error: " + e.getMessage() + "\nRe-enter stock to remove: ");
                            }
                        }

                        CartItem item = items.get(id);
                        int newQuantity = item.getQuantity() - stock;
                        if (newQuantity <= 0) {
                            items.remove(id);
                            System.out.println("Item '" + item.getProduct().getProductName() + "' removed from cart.");
                        } else {
                            item.setQuantity(newQuantity);
                        }
                    } else {
                        System.out.println("No item yet");
                    }
                }
                case 3 -> {
                    if (!items.isEmpty()) {
                        double total = items.values().stream()
                                .mapToDouble(item -> item.getProduct().getSellingPrice() * item.getQuantity()).sum();
                        System.out.println("Total cost: " + total);
                    } else {
                        System.out.println("No item yet");
                    }
                }
                case 4 -> {
                    if (!items.isEmpty()) {
                        items.values().stream().sorted(Comparator.comparing(item -> item.getProduct().getProductName()))
                                .forEach(item -> System.out.println("Item ID: " + item.getProduct().getProductId()
                                        + "\nItem name: " + item.getProduct().getProductName() + "\nSelling price: "
                                        + item.getProduct().getSellingPrice() + "\nQuantity: " + item.getQuantity()
                                        + "\n"));
                    } else {
                        System.out.println("No item yet");
                    }
                }
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter choice: ");
            }
        }
    }
}