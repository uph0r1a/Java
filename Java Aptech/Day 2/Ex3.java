import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Ex3 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static class Product {
        private String ID, name, description;
        private double price;

        public Product() {
        }

        public Product(String iD, String name, String description, double price) {
            ID = iD;
            this.name = name;
            this.description = description;
            this.price = price;
        }

        public String getID() {
            return ID;
        }

        public void setID(String iD) {
            ID = iD;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public void doSale() {
            System.out.print("Enter discount amount: ");
            double amount;
            while (true) {
                try {
                    amount = Double.parseDouble(br.readLine());
                    if (amount >= 0) {
                        break;
                    }
                    System.out.print("Invalid discount amount\nRe-enter discount amount: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            price -= amount;
            if (price <= 0) {
                price = 0;
            }
        }

        @Override
        public String toString() {
            return "ID: " + ID + "\nName: " + name + "\nDescription: " + description + "\nPrice: " + price + "\n";
        }
    }

    public static void main(String[] args) {
        Product product1 = new Product();
        Product product2 = new Product("1", "A", "a", 10);
        product1.doSale();
        product2.doSale();

        System.out.println("Product 1:\n" + product1.toString() + "Product 2:\n" + product2.toString());
    }
}
