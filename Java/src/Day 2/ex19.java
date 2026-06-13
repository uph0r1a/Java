public class ex19 {
    public static void main(String[] args) {

        double shares = 1000;
        double buyPrice = 32.87;
        double sellPrice = 33.92;
        double commissionRate = 0.02;
        double amountPaid = shares * buyPrice;
        double buyCommission = amountPaid * commissionRate;
        double amountSold = shares * sellPrice;
        double sellCommission = amountSold * commissionRate;
        double profit = amountSold - sellCommission - amountPaid - buyCommission;

        System.out.printf("Amount paid for stock: $%.2f%n", amountPaid);
        System.out.printf("Buy commission: $%.2f%n", buyCommission);
        System.out.printf("Amount sold for: $%.2f%n", amountSold);
        System.out.printf("Sell commission: $%.2f%n", sellCommission);
        System.out.printf("Profit/Loss: $%.2f%n", profit);
    }
}