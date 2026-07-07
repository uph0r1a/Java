import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex5 {
    static class Payroll {
        private String name;
        private int IDNumber, numberHour;
        private double payRate;

        public Payroll(String name, int iDNumber, double payRate, int numberHour)
                throws EmptyEmployeeName, InvalidIDNumber, InvalidHourWorked, InvalidPayRate {
            if (name.isEmpty()) {
                throw new EmptyEmployeeName("Empty name");
            }

            if (iDNumber <= 0) {
                throw new InvalidIDNumber("Invalid ID number");
            }

            if (numberHour < 0 || numberHour > 84) {
                throw new InvalidHourWorked("Invalid number of hour worked");
            }

            if (payRate < 0 || payRate > 25) {
                throw new InvalidPayRate("Invalid hourly pay rate: ");
            }
            this.name = name;
            IDNumber = iDNumber;
            this.payRate = payRate;
            this.numberHour = numberHour;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) throws EmptyEmployeeName {
            if (name.isEmpty()) {
                throw new EmptyEmployeeName("Empty name");
            }
            this.name = name;
        }

        public int getIDNumber() {
            return IDNumber;
        }

        public void setIDNumber(int iDNumber) throws InvalidIDNumber {
            if (iDNumber <= 0) {
                throw new InvalidIDNumber("Invalid ID number");
            }
            IDNumber = iDNumber;
        }

        public double getPayRate() {
            return payRate;
        }

        public void setPayRate(double payRate) throws InvalidPayRate {
            if (payRate < 0 || payRate > 25) {
                throw new InvalidPayRate("Invalid hourly pay rate: ");
            }
            this.payRate = payRate;
        }

        public int getNumberHour() {
            return numberHour;
        }

        public void setNumberHour(int numberHour) throws InvalidHourWorked {
            if (numberHour < 0 || numberHour > 84) {
                throw new InvalidHourWorked("Invalid number of hour worked");
            }
            this.numberHour = numberHour;
        }

        public double grossPay() {
            return numberHour * payRate;
        }
    }

    static class EmptyEmployeeName extends Exception {
        public EmptyEmployeeName(String message) {
            super(message);
        }
    }

    static class InvalidIDNumber extends Exception {
        public InvalidIDNumber(String message) {
            super(message);
        }
    }

    static class InvalidHourWorked extends Exception {
        public InvalidHourWorked(String message) {
            super(message);
        }
    }

    static class InvalidPayRate extends Exception {
        public InvalidPayRate(String message) {
            super(message);
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException, EmptyEmployeeName,
            InvalidIDNumber, InvalidHourWorked, InvalidPayRate {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Payroll payroll = null;

        while (payroll == null) {
            try {
                System.out.print("Enter employee name: ");
                String name = br.readLine();

                System.out.print("Enter ID number: ");
                int id = Integer.parseInt(br.readLine());

                System.out.print("Enter hourly pay rate: ");
                double rate = Double.parseDouble(br.readLine());

                System.out.print("Enter number of hours: ");
                int hours = Integer.parseInt(br.readLine());

                payroll = new Payroll(name, id, rate, hours);

            } catch (EmptyEmployeeName | InvalidIDNumber | InvalidHourWorked | InvalidPayRate | NumberFormatException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        System.out.println("\nEmployee created successfully:" + payroll);
        System.out.printf("Gross pay: $%.2f%n", payroll.grossPay());
    }
}
