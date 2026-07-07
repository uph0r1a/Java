import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;

public class ex10 {
    static class Employee {
        private String name, number;
        private LocalDate date;

        public Employee(String name, String number, LocalDate date) throws InvalidEmployeeNumber {
            setNumber(number);
            this.name = name;
            this.date = date;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNumber() {
            return number;
        }

        public void setNumber(String number) throws InvalidEmployeeNumber {
            if (number == null || !number.matches("^\\d{3}-[A-M]$")) {
                throw new InvalidEmployeeNumber("Invalid employee number: " + number);
            }
            this.number = number;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        @Override
        public String toString() {
            return "Employee name: " + name + "\nEmployee number: " + number + "\nHire date: " + date;
        }
    }

    static class ProductionWorker extends Employee {
        private int shift;
        private double hourlyPayRate;

        public ProductionWorker(String name, String number, LocalDate date, int shift, double hourlyPayRate)
                throws InvalidEmployeeNumber, InvalidShift, InvalidPayRate {
            super(name, number, date);
            setShift(shift);
            setHourlyPayRate(hourlyPayRate);
        }

        public int getShift() {
            return shift;
        }

        public void setShift(int shift) throws InvalidShift {
            if (shift != 1 && shift != 2) {
                throw new InvalidShift("Invalid shift: " + shift);
            }
            this.shift = shift;
        }

        public double getHourlyPayRate() {
            return hourlyPayRate;
        }

        public void setHourlyPayRate(double hourlyPayRate) throws InvalidPayRate {
            if (hourlyPayRate < 0) {
                throw new InvalidPayRate("Invalid hourly pay rate: " + hourlyPayRate);
            }
            this.hourlyPayRate = hourlyPayRate;
        }

        @Override
        public String toString() {
            return super.toString() + "\nShift: " + (shift == 1 ? "Day" : "Night")
                    + "\nHourly pay rate: " + hourlyPayRate;
        }
    }

    static class InvalidEmployeeNumber extends Exception {
        public InvalidEmployeeNumber(String message) {
            super(message);
        }
    }

    static class InvalidShift extends Exception {
        public InvalidShift(String message) {
            super(message);
        }
    }

    static class InvalidPayRate extends Exception {
        public InvalidPayRate(String message) {
            super(message);
        }
    }

    public static void main(String[] args)
            throws NumberFormatException, IOException, InvalidEmployeeNumber, InvalidShift, InvalidPayRate {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter employee name: ");
        String name = br.readLine();

        System.out.print("Enter employee number: ");
        String number;
        while (true) {
            try {
                number = br.readLine();
                if (number.matches("^\\d{3}-[A-M]$")) {
                    break;
                }
                System.out.print("Invalid format\nRe-enter employee number: ");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        System.out.print("Enter hire date (yyyy-MM-dd): ");
        LocalDate date = LocalDate.parse(br.readLine());

        System.out.print("Enter shift: ");
        int shift;
        while (true) {
            try {
                shift = Integer.parseInt(br.readLine());
                if (shift == 1 || shift == 2) {
                    break;
                }
                System.out.print("Invalid shift\nRe-enter shift: ");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        System.out.print("Enter hourly pay rate: ");
        double rate = Double.parseDouble(br.readLine());

        ProductionWorker worker = new ProductionWorker(name, number, date, shift, rate);
        System.out.println(worker.toString());
    }
}
