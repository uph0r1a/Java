import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ex12_13 {

    static class SavingsAccount {
        private double rate;
        private double balance;
        private double totalInterest = 0;

        public SavingsAccount(double rate, double balance) {
            this.rate = rate;
            this.balance = balance;
        }

        public void deposit(double amount) {
            balance += amount;
        }

        public void withdrawal(double amount) {
            balance -= amount;
        }

        public void addMonthlyInterest() {
            double interest = balance * rate / 12;
            balance += interest;
            totalInterest += interest;
        }

        public double getBalance() {
            return balance;
        }

        public double getTotalInterest() {
            return totalInterest;
        }
    }

    public static void main(String[] args) {

        SavingsAccount acc = new SavingsAccount(0.05, 500.00);

        double totalDeposits = 0;
        double totalWithdrawals = 0;

        try {
            List<String> deposits = Files.readAllLines(Path.of("files/Deposits.txt"));
            for (String line : deposits) {
                double amount = Double.parseDouble(line);
                acc.deposit(amount);
                totalDeposits += amount;
            }

            List<String> withdrawals = Files.readAllLines(Path.of("files/Withdrawals.txt"));
            for (String line : withdrawals) {
                double amount = Double.parseDouble(line);
                acc.withdrawal(amount);
                totalWithdrawals += amount;
            }

            acc.addMonthlyInterest();

            System.out.printf(
                    "Ending Balance: %.2f%nTotal Deposits: %.2f%nTotal Withdrawals: %.2f%nTotal Interest Earned: %.2f%n",
                    acc.getBalance(), totalDeposits, totalWithdrawals, acc.getTotalInterest());
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }
}