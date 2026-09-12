import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        public Product(String id, String name, double price, int quantity) {
            this.id = id;
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
            if (quantity <= 0) {
                System.out.println("Error: Quantity to add must be greater than 0");
                return;
            }
            this.quantity += quantity;
        }

        @Override
        public void removeStock(int quantity) {
            if (quantity <= 0) {
                System.out.println("Error: Quantity to remove must be greater than 0");
                return;
            }
            if (this.quantity < quantity) {
                System.out.println("Error: Cant remove more than the stock");
                return;
            }
            this.quantity -= quantity;
        }

        @Override
        public void displayInfo() {
            System.out.println("\nID: " + id + "\nName: " + name + "\nPrice: " + price + "\nQuantity: " + quantity);
        }
    }

    public static class Food extends Product {
        private String expirationDate;
        private boolean isPerishable;

        public Food(String id, String name, double price, int quantity, String expirationDate,
                boolean isPerishable) {
            super(id, name, price, quantity);
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

        public Electronics(String id, String name, double price, int quantity, String warrantyPeriod, String brand) {
            super(id, name, price, quantity);
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
            String updated = warrantyPeriod + " + " + additionalPeriod;
            System.out.println(warrantyPeriod + " extended to: " + updated);
            this.warrantyPeriod = updated;
        }

        @Override
        public void displayInfo() {
            super.displayInfo();
            System.out.println("\nWarranty Period: " + warrantyPeriod + "\nBrand: " + brand);
        }
    }

    public static class Household extends Product {
        private String material, usage;

        public Household(String id, String name, double price, int quantity, String material, String usage) {
            super(id, name, price, quantity);
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
            System.out.print("Enter product ID: ");
            String id;
            while (true) {
                id = br.readLine().strip();
                if (!products.containsKey(id)) {
                    break;
                }
                System.out.print("ID already in use\nRe-enter product ID: ");
            }

            System.out.print("Enter product name: ");
            String name = br.readLine().strip();

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
                    System.out.print("Enter expiration date (uuuu-MM-dd): ");
                    String expDate;
                    while (true) {
                        expDate = br.readLine().strip();
                        if (isValidDate(expDate, pattern)) {
                            break;
                        }
                        System.out.print("Invalid expiration date\nRe-enter expiration date (uuuu-MM-dd): ");
                    }

                    int isPerishable;
                    while (true) {
                        System.out.print("Perishability 1) Yes 2) No: ");
                        try {
                            isPerishable = Integer.parseInt(br.readLine());
                            if (isPerishable == 1 || isPerishable == 2) {
                                break;
                            }
                            System.out.println("Invalid choice");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    Food food = new Food(id, name, price, quantity, expDate, isPerishable == 1);
                    products.put(food.getId(), food);
                    System.out.println("Food added successfully");
                }
                case 2 -> {
                    System.out.print("Enter warranty period: ");
                    String warrantyPeriod = br.readLine().strip();

                    System.out.print("Enter brand: ");
                    String brand = br.readLine().strip();

                    Electronics electronics = new Electronics(id, name, price, quantity, warrantyPeriod, brand);
                    products.put(electronics.getId(), electronics);
                    System.out.println("Electronics added successfully");
                }
                case 3 -> {
                    System.out.print("Enter material: ");
                    String material = br.readLine().strip();

                    System.out.print("Enter usage: ");
                    String usage = br.readLine().strip();

                    Household household = new Household(id, name, price, quantity, material, usage);
                    products.put(household.getId(), household);
                    System.out.println("Household added successfully");
                }
            }
        }

        public void remove() throws IOException {
            if (products.isEmpty()) {
                System.out.println("No products yet");
            } else {
                System.out.print("Enter product ID: ");
                String id = br.readLine().strip();

                if (products.remove(id) != null) {
                    System.out.println("Removed");
                } else {
                    System.out.println("Product doesn't exist");
                }
            }
        }

        public void search() throws IOException {
            if (products.isEmpty()) {
                System.out.println("No products yet");
            } else {
                System.out.print("Enter product ID: ");
                String id = br.readLine().strip();

                Product product = products.get(id);
                if (product == null) {
                    System.out.println("Product not found");
                } else {
                    product.displayInfo();
                }
            }
        }

        public void findByName() throws IOException {
            if (products.isEmpty()) {
                System.out.println("No products yet");
                return;
            }

            System.out.print("Enter product name: ");
            String name = br.readLine().strip();

            List<Product> results = products.values().stream()
                    .filter(p -> p.getName().equalsIgnoreCase(name)).toList();

            if (results.isEmpty()) {
                System.out.println("No product found with that name");
            } else {
                results.forEach(Product::displayInfo);
            }
        }

        public void filterByCategory() throws IOException {
            if (products.isEmpty()) {
                System.out.println("No products yet");
                return;
            }

            System.out.print("Enter category (food/electronics/household): ");
            String category;
            while (true) {
                category = br.readLine().strip();
                if (category.equalsIgnoreCase("food") || category.equalsIgnoreCase("electronics")
                        || category.equalsIgnoreCase("household")) {
                    break;
                }
                System.out.print("Invalid category\nRe-enter category (food/electronics/household): ");
            }
            final String c = category;

            List<Product> results = products.values().stream().filter(p -> switch (c.toLowerCase()) {
                case "food" -> p instanceof Food;
                case "electronics" -> p instanceof Electronics;
                case "household" -> p instanceof Household;
                default -> false;
            }).toList();

            if (results.isEmpty()) {
                System.out.println("No products found in that category");
            } else {
                results.forEach(Product::displayInfo);
            }
        }

        public void updateFoodExpiration() throws IOException {
            if (products.isEmpty()) {
                System.out.println("No products yet");
                return;
            }

            System.out.print("Enter product ID: ");
            String id = br.readLine().strip();
            Product product = products.get(id);

            if (product == null) {
                System.out.println("Product not found");
            } else if (!(product instanceof Food food)) {
                System.out.println("This product is not a Food item");
            } else {
                System.out.print("Enter new expiration date (uuuu-MM-dd): ");
                String newDate;
                while (true) {
                    newDate = br.readLine().strip();
                    if (isValidDate(newDate, pattern)) {
                        break;
                    }
                    System.out.print("Invalid date\nRe-enter expiration date (uuuu-MM-dd): ");
                }
                food.updateExpirationDate(newDate);
                System.out.println("Expiration date updated");
            }
        }

        public void extendElectronicsWarranty() throws IOException {
            if (products.isEmpty()) {
                System.out.println("No products yet");
                return;
            }

            System.out.print("Enter product ID: ");
            String id = br.readLine().strip();
            Product product = products.get(id);

            if (product == null) {
                System.out.println("Product not found");
            } else if (!(product instanceof Electronics electronics)) {
                System.out.println("This product is not an Electronics item");
            } else {
                System.out.print("Enter additional warranty period: ");
                String additionalPeriod = br.readLine().strip();
                electronics.extendWarranty(additionalPeriod);
            }
        }

        public void updateHouseholdUsage() throws IOException {
            if (products.isEmpty()) {
                System.out.println("No products yet");
                return;
            }

            System.out.print("Enter product ID: ");
            String id = br.readLine().strip();
            Product product = products.get(id);

            if (product == null) {
                System.out.println("Product not found");
            } else if (!(product instanceof Household household)) {
                System.out.println("This product is not a Household item");
            } else {
                System.out.print("Enter new usage: ");
                String newUsage = br.readLine().strip();
                household.updateUsage(newUsage);
                System.out.println("Usage updated");
            }
        }

        public void display() {
            if (products.isEmpty()) {
                System.out.println("No products yet");
            } else {
                products.values().forEach(Product::displayInfo);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        InventoryManager manager = new InventoryManager();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    ===== WAREHOUSE MANAGEMENT =====
                    1) Add product
                    2) Remove product
                    3) Search product by ID
                    4) Find products by name
                    5) Filter by category
                    6) Update food expiration date
                    7) Extend electronics warranty
                    8) Update household usage
                    9) Display all products
                    0) Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 9) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (choice) {
                case 1 -> manager.add();
                case 2 -> manager.remove();
                case 3 -> manager.search();
                case 4 -> manager.findByName();
                case 5 -> manager.filterByCategory();
                case 6 -> manager.updateFoodExpiration();
                case 7 -> manager.extendElectronicsWarranty();
                case 8 -> manager.updateHouseholdUsage();
                case 9 -> manager.display();
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}