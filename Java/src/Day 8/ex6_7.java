import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ex6_7 {
    static class RetailItem {
        private String description;
        private int unitsOnHand;
        private double price;

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

    static class CashRegister {
        private RetailItem item;
        private double quantity;

        public CashRegister(RetailItem item, double quantity) {
            this.item = item;
            this.quantity = quantity;
        }

        public double getSubtotal() {
            return item.getPrice() * quantity;
        }

        public double getTax() {
            return getSubtotal() * 0.06;
        }

        public double getTotal() {
            return getSubtotal() + getTax();
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter quantity: ");
        double quantity = Double.parseDouble(br.readLine());

        CashRegister register = new CashRegister(new RetailItem("A", 5, 10.0), quantity);

        System.out.println("Sale subtotal: " + register.getSubtotal() + "\nSale tax: " + register.getTax() + "\nTotal: "
                + register.getTotal());

        try {
            StringBuilder sb = new StringBuilder();

            sb.append("SALES RECEIPT\nUnit Price: $" + (register.getSubtotal() / quantity) + "\nQuantity: " + quantity
                    + "\nSubtotal: " + register.getSubtotal() + "\nSales Tax: $" + register.getTax() + "\nTotal: $"
                    + register.getTotal());

            Files.writeString(Path.of("files/saleReceipt.txt"), sb.toString(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
