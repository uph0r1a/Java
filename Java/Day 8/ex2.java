public class ex2 {
    static public class BankAccount {
        private double balance;

        public BankAccount() {
            balance = 0.0;
        }

        public BankAccount(BankAccount acc) {
            this.balance = acc.balance;
        }

        public BankAccount(double startBalance) {
            balance = startBalance;
        }

        public BankAccount(String str) {
            balance = Double.parseDouble(str);
        }

        public void deposit(double amount) {
            balance += amount;
        }

        public void deposit(String str) {
            balance += Double.parseDouble(str);
        }

        public void withdraw(double amount) {
            balance -= amount;
        }

        public void withdraw(String str) {
            balance -= Double.parseDouble(str);
        }

        public void setBalance(double b) {
            balance = b;
        }

        public void setBalance(String str) {
            balance = Double.parseDouble(str);
        }

        public double getBalance() {
            return balance;
        }
    }

    public static void main(String[] args) {

        BankAccount acc1 = new BankAccount();
        System.out.println(acc1.getBalance());

        BankAccount acc2 = new BankAccount(1000);
        acc2.deposit(500);
        acc2.withdraw("200");
        System.out.println(acc2.getBalance());

        BankAccount acc3 = new BankAccount("2500");
        System.out.println(acc3.getBalance());

        BankAccount acc4 = new BankAccount(acc3);
        System.out.println(acc4.getBalance());
    }
}
