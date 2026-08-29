import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Ex9 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    public static String pattern = "uuuu-MM-dd";

    public static boolean isValidDate(String dateStr, String formatPattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatPattern)
                .withResolverStyle(ResolverStyle.STRICT);

        try {
            LocalDate.parse(dateStr, formatter);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static interface IProduct {
        public void addStock(int quantity);

        public void removeStock(int quantity);

        public void displayInfo();
    }

    public static class Product implements IProduct {
        private String id, name;
        private double price;
        private int quantity;

        public Product(String name, double price, int quantity) {
            this.id = UUID.randomUUID().toString();
            this.name = name;
            this.price = price;
            this.quantity = quantity;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        @Override
        public void addStock(int quantity) {
            this.quantity += quantity;
        }

        @Override
        public void removeStock(int quantity) {
            if (this.quantity < quantity) {
                System.out.println("Error: Cant remove more than the stock");
                return;
            }
            this.quantity -= quantity;
        }

        @Override
        public void displayInfo() {
            System.out.println("\nID" + id + "\nName: " + name + "\nPrice: " + price + "\nQuantity: " + quantity);
        }
    }

    public static class Food extends Product {
        private String expirationDate,;
        private boolean isPerishable;

        public Food(String name, double price, int quantity, String expirationDate, boolean isPerishable) {
            super(name, price, quantity);
            this.expirationDate = expirationDate;
            this.isPerishable = isPerishable;
        }

        public String getExpirationDate() {
            return expirationDate;
        }

        public void setExpirationDate(String expirationDate) {
            this.expirationDate = expirationDate;
        }

        public boolean isPerishable() {
            return isPerishable;
        }

        public void setPerishable(boolean isPerishable) {
            this.isPerishable = isPerishable;
        }

        public void updateExpirationDate(String updateExpDate) {
            this.expirationDate = updateExpDate;
        }

        @Override
        public void displayInfo() {
            super.displayInfo();
            System.out.println(
                    "\nExpiration Date: " + expirationDate + "\nPerishability: " + (isPerishable ? "Yes" : "No"));
        }
    }

    public static class Electronics extends Product {
        private String warrantyPeriod, brand;

        public Electronics(String name, double price, int quantity, String warrantyPeriod, String brand) {
            super(name, price, quantity);
            this.warrantyPeriod = warrantyPeriod;
            this.brand = brand;
        }

        public String getWarrantyPeriod() {
            return warrantyPeriod;
        }

        public void setWarrantyPeriod(String warrantyPeriod) {
            this.warrantyPeriod = warrantyPeriod;
        }

        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }

        public void extendWarranty(String additionalPeriod) {
            System.out.println(getWarrantyPeriod() + " Extended to: " + additionalPeriod);
        }

        @Override
        public void displayInfo() {
            super.displayInfo();
            System.out.println("\nWarranty Period: " + warrantyPeriod + "\nBrand: " + brand);
        }
    }

    public static class Household extends Product {
        private String material, usage;

        public Household(String name, double price, int quantity, String material, String usage, String updateUsage) {
            super(name, price, quantity);
            this.material = material;
            this.usage = usage;
        }

        public String getMaterial() {
            return material;
        }

        public void setMaterial(String material) {
            this.material = material;
        }

        public String getUsage() {
            return usage;
        }

        public void setUsage(String usage) {
            this.usage = usage;
        }

        public void updateUsage(String updateUsage) {
            this.usage = updateUsage;
        }

        @Override
        public void displayInfo() {
            super.displayInfo();
            System.out.println("\nMaterial: " + material + "\nUsage: " + usage);
        }
    }

    public static class InventoryManager {
        private Map<String, Product> products;

        public InventoryManager() {
            products = new HashMap<>();
        }

        public void add() throws IOException {
            System.out.print("Enter product name: ");
            String name = br.readLine();

            System.out.print("Enter product price: ");
            double price;
            while (true) {
                try {
                    price = Double.parseDouble(br.readLine());
                    if (price >= 0) {
                        break;
                    }
                    System.out.print("Invalid price\nRe-enter product price: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter product quantity: ");
            int quantity;
            while (true) {
                try {
                    quantity = Integer.parseInt(br.readLine());
                    if (quantity >= 0) {
                        break;
                    }
                    System.out.print("Invalid quantity\nRe-enter product quantity: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter product type 1) Food 2) Electronics 3) Household: ");
            int type;
            while (true) {
                try {
                    type = Integer.parseInt(br.readLine());
                    if (type >= 1 && type <= 3) {
                        break;
                    }
                    System.out.print("Invalid type\nRe-enter product type: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (type) {
                case 1 -> {
                    System.out.println("Enter expiration date: ");
                    String expDate;
                    while (true) {
                        try {
                            expDate = br.readLine();
                            if (isValidDate(expDate, pattern)) {
                                break;
                            }
                            System.out.println("Invalid expiration date\nRe-enter expiration date: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    int isPerishable;
                    while (true) {
                        System.out.println("Perishability 1) Yes 2) No: ");
                        try {
                            isPerishable = Integer.parseInt(br.readLine());
                            if (isPerishable == 1 || isPerishable == 2) {
                                break;
                            }
                            System.out.println("Invalid choice\n");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    Food food = new Food(name, price, quantity, expDate, isPerishable == 1 ? true : false);
                    products.put(food.getId(), food);
                }
                case 2 -> {
                    System.out.println("Enter warranty period: ");
                    String warrantyPeriod = br.readLine();

                    System.out.println("Enter brand: ");
                    String brand = br.readLine();

                    Electronics electronics = new Electronics(name, price, quantity, warrantyPeriod, brand);
                    products.put(electronics.getId(), electronics);
                }
                case 3 -> {
                    System.out.println("Enter material: ");
                    String material = br.readLine();

                    System.out.println("Enter usage: ");
                    String usage = br.readLine();

                    Household household = new Household(name, price, quantity, material, usage, usage);
                    products.put(household.getId(), household);
                }
                default -> System.out.print("Invalid type\nRe-enter product type: ");
            }
        }

        public void remove() {
            if (products.isEmpty()) {
                System.out.println("No products yet");
            } else {
                System.out.println("Enter product ID: ");
                String id = br.readLine().strip();

                if (products.remove(id) != null) {
                    System.out.println("Removed");
                } else {
                    System.out.println("Product dont exist");
                }
            }
        }

        
    }

    public static void main(String[] args) {

    }
}
