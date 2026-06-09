public class stockCommission {
    public static void main(String[] args) {
        double stockAlone = 21.77 * 600, commission = stockAlone * 0.02, total = stockAlone + commission;

        System.out.print("•  The amount paid for the stock alone (without the commission): " + stockAlone
                + "\n•  The amount of the commission: " + commission
                + "\n•  The total amount paid (for the stock plus the commission): " + total);
    }
}
