public class ex9 {
    static abstract class BankAccount {
        private double balance, interestRate, serviceCharge;
        private int numberDeposit, numberWithdrawal;

        public BankAccount(double balance, double interestRate) {
            this.balance = balance;
            this.interestRate = interestRate;
            this.serviceCharge = 0.0;
            this.numberDeposit = 0;
            this.numberWithdrawal = 0;
        }

        public void deposit(double deposit) {
            balance += deposit;
            numberDeposit++;
        }

        public void withdraw(double withdraw) {
            balance -= withdraw;
            numberWithdrawal++;
        }

        public void calcInterest() {
            double monthlyInterestRate = interestRate / 12;
            double monthlyInterest = balance * monthlyInterestRate;
            balance += monthlyInterest;
        }

        public void monthlyProcess() {
            balance -= serviceCharge;
            calcInterest();
            numberDeposit = 0;
            numberWithdrawal = 0;
            serviceCharge = 0;
        }

        public double getBalance() {
            return balance;
        }

        public double getInterestRate() {
            return interestRate;
        }

        public double getServiceCharge() {
            return serviceCharge;
        }

        public int getNumberDeposit() {
            return numberDeposit;
        }

        public int getNumberWithdrawal() {
            return numberWithdrawal;
        }

        public void setBalance(double balance) {
            this.balance = balance;
        }

        public void setInterestRate(double interestRate) {
            this.interestRate = interestRate;
        }

        public void setServiceCharge(double serviceCharge) {
            this.serviceCharge = serviceCharge;
        }

        public void setNumberDeposit(int numberDeposit) {
            this.numberDeposit = numberDeposit;
        }

        public void setNumberWithdrawal(int numberWithdrawal) {
            this.numberWithdrawal = numberWithdrawal;
        }
    }

    static class SavingAccount extends BankAccount {
        private boolean status;

        public SavingAccount(double balance, double interestRate) {
            super(balance, interestRate);
            status = getBalance() >= 25;
        }

        public boolean isActive() {
            return status;
        }

        @Override
        public void deposit(double deposit) {
            if (!status && getBalance() + deposit >= 25) {
                status = true;
            }
            super.deposit(deposit);
        }

        @Override
        public void withdraw(double withdraw) {
            if (status) {
                super.withdraw(withdraw);
            } else {
                System.out.println("Withdrawal denied: account is inactive.");
            }
        }

        @Override
        public void monthlyProcess() {
            if (getNumberWithdrawal() > 4) {
                double extraCharge = getNumberWithdrawal() - 4;
                setServiceCharge(getServiceCharge() + extraCharge);
            }

            if (getBalance() - getServiceCharge() < 25) {
                status = false;
            }

            super.monthlyProcess();
        }
    }

    public static void main(String[] args) {
        SavingAccount account = new SavingAccount(100.0, 0.02);

        System.out.printf("Starting balance: $%.2f, Active: %b%n", account.getBalance(), account.isActive());

        for (int i = 1; i <= 6; i++) {
            account.withdraw(10.0);
        }
        System.out.printf("After 6 withdrawals: $%.2f, Withdrawals: %d%n", account.getBalance(),
                account.getNumberWithdrawal());
        account.deposit(5.0);
        System.out.printf("After a $5 deposit: $%.2f%n", account.getBalance());
        account.monthlyProcess();
        System.out.printf(
                "After monthlyProcess: $%.2f, Active: %b, Service charge applied, deposits/withdrawals reset%n",
                account.getBalance(), account.isActive());
        account.withdraw(1.0);
        account.deposit(50.0);
        System.out.printf("After reactivating deposit: $%.2f, Active: %b%n", account.getBalance(), account.isActive());
    }
}