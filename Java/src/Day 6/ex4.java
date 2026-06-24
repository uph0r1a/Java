import java.util.Arrays;

public class ex4 {
    static class RetailItem {
        String description;
        int unitsOnHand;
        double price;

        public RetailItem(String description, int unitsOnHand, double price) {
            this.description = description;
            this.unitsOnHand = unitsOnHand;
            this.price = price;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getUnitsOnHand() {
            return unitsOnHand;
        }

        public void setUnitsOnHand(int unitsOnHand) {
            this.unitsOnHand = unitsOnHand;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        @Override
        public String toString() {
            return "RetailItem [description=" + description + ", unitsOnHand=" + unitsOnHand + ", price=" + price + "]";
        }
    }

    public static void main(String[] args) {
        RetailItem[] items = {
                new RetailItem("Jacket", 12, 59.95),
                new RetailItem("Designer Jeans", 40, 34.95),
                new RetailItem("Shirt", 20, 24.95)
        };

        System.out.println(Arrays.toString(items));
    }
}
