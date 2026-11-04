
import java.io.*;
import java.util.*;

public class Ex15 {

    static class CosmeticProduct implements
            Comparable<CosmeticProduct>, Serializable {

        private int productID;
        private String productName;
        private double price;

        public CosmeticProduct() {
        }

        public CosmeticProduct(int productID, String productName,
                               double price) {
            this.productID = productID;
            this.productName = productName;
            this.price = price;
        }

        public int getProductID() {
            return productID;
        }

        public void setProductID(int productID) {
            this.productID = productID;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        // Sort by price in ascending order
        @Override
        public int compareTo(CosmeticProduct other) {
            return Double.compare(this.price, other.price);
        }

        @Override
        public String toString() {
            return String.format(
                "Product ID: %d | Product Name: %s | Price: $%.2f",
                productID, productName, price
            );
        }
    }

    public static void main(String[] args) {
        BufferedReader br =
            new BufferedReader(new InputStreamReader(System.in));

        String fileName = "cosmetics.dat";

        try {
            List<CosmeticProduct> products = new ArrayList<>();

            // Input exactly 3 cosmetic products
            System.out.println("Enter information for 3 cosmetic products:");

            for (int i = 0; i < 3; i++) {
                System.out.println("\nProduct " + (i + 1));

                int productID;

                while (true) {
                    System.out.print("Product ID: ");
                    String input = br.readLine();

                    if (input == null) {
                        throw new EOFException("No more keyboard input.");
                    }

                    try {
                        productID = Integer.parseInt(input.trim());
                        break;
                    } catch (NumberFormatException e) {
                        System.out.println(
                            "Invalid product ID. Enter an integer."
                        );
                    }
                }

                System.out.print("Product name: ");
                String productName = br.readLine();

                if (productName == null) {
                    throw new EOFException("No more keyboard input.");
                }

                productName = productName.trim();

                // Validate price
                double price;

                while (true) {
                    System.out.print("Price: ");
                    String input = br.readLine();

                    if (input == null) {
                        throw new EOFException("No more keyboard input.");
                    }

                    try {
                        price = Double.parseDouble(input.trim());

                        if (!Double.isFinite(price) || price <= 0) {
                            System.out.println(
                                "Price must be a valid number greater than 0."
                            );
                        } else {
                            break;
                        }

                    } catch (NumberFormatException e) {
                        System.out.println("Invalid price. Try again.");
                    }
                }

                products.add(
                    new CosmeticProduct(productID, productName, price)
                );

                System.out.println("Product added successfully.");
            }

            // Serialize products
            try (ObjectOutputStream oos =
                     new ObjectOutputStream(
                         new FileOutputStream(fileName))) {

                oos.writeObject(products);

                System.out.println(
                    "\nProducts saved successfully to " + fileName
                );

            } catch (IOException e) {
                System.out.println(
                    "Error saving products: " + e.getMessage()
                );
                return;
            }

            // Deserialize products into a new list
            List<CosmeticProduct> loadedProducts;

            try (ObjectInputStream ois =
                     new ObjectInputStream(
                         new FileInputStream(fileName))) {

                @SuppressWarnings("unchecked")
                List<CosmeticProduct> result =
                    (List<CosmeticProduct>) ois.readObject();

                loadedProducts = result;

                System.out.println("Products loaded successfully.");

            } catch (IOException e) {
                System.out.println(
                    "Error reading products: " + e.getMessage()
                );
                return;

            } catch (ClassNotFoundException e) {
                System.out.println(
                    "Error: CosmeticProduct class could not be found."
                );
                return;
            }

            // Sort by price in ascending order
            Collections.sort(loadedProducts);

            // Display sorted products
            System.out.println(
                "\n========== PRODUCTS SORTED BY PRICE (ASCENDING) =========="
            );

            for (CosmeticProduct product : loadedProducts) {
                System.out.println(product);
            }

        } catch (IOException e) {
            System.out.println(
                "Error reading keyboard input: " + e.getMessage()
            );
        }
    }
}
