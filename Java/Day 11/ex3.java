public class ex3 {
    static class RetailItem {
        private String description;
        private int unitsOnHand;
        private double price;

        public RetailItem(String description, int unitsOnHand, double price)
                throws InvalidNegativeUnits, InvalidNegativePrice {
            this.description = description;
            setUnitsOnHand(unitsOnHand);
            setPrice(price);
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

        public void setUnitsOnHand(int unitsOnHand) throws InvalidNegativeUnits {
            if (unitsOnHand < 0) {
                throw new InvalidNegativeUnits("Units on hand cannot be negative: " + unitsOnHand);
            }
            this.unitsOnHand = unitsOnHand;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) throws InvalidNegativePrice {
            if (price < 0) {
                throw new InvalidNegativePrice("Price cannot be negative: " + price);
            }
            this.price = price;
        }

        @Override
        public String toString() {
            return "RetailItem [description=" + description + ", unitsOnHand=" + unitsOnHand + ", price=" + price + "]";
        }
    }

    static class InvalidNegativePrice extends Exception {
        public InvalidNegativePrice(String message) {
            super(message);
        }
    }

    static class InvalidNegativeUnits extends Exception {
        public InvalidNegativeUnits(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        try {
            RetailItem item1 = new RetailItem("Jacket", 12, 59.99);
            System.out.println("Created: " + item1);
        } catch (InvalidNegativeUnits | InvalidNegativePrice e) {
            System.out.println("Error: " + e.getMessage());
        }

        try {
            RetailItem item2 = new RetailItem("Hat", -5, 19.99);
            System.out.println("Created: " + item2);
        } catch (InvalidNegativeUnits | InvalidNegativePrice e) {
            System.out.println("Error: " + e.getMessage());
        }

        try {
            RetailItem item3 = new RetailItem("Scarf", 10, -4.50);
            System.out.println("Created: " + item3);
        } catch (InvalidNegativeUnits | InvalidNegativePrice e) {
            System.out.println("Error: " + e.getMessage());
        }

        try {
            RetailItem item4 = new RetailItem("Gloves", 20, 9.99);
            System.out.println("Created: " + item4);
            item4.setPrice(-1.0);
        } catch (InvalidNegativeUnits | InvalidNegativePrice e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}