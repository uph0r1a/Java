import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;

public class ex1_2 {
    static class Employee {
        private String name, number;
        private LocalDate date;

        public Employee(String name, String number, LocalDate date) {
            this.name = name;
            this.number = number;
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

        public void setNumber(String number) {
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
            return "Employee name: " + name + "\nEmpoyee number: " + number + "\nHire date: " + date;
        }
    }

    static class ProductionWorker extends Employee {
        private int shift;
        private double hourlyPayRate;

        public ProductionWorker(String name, String number, LocalDate date, int shift, double hourlyPayRate) {
            super(name, number, date);
            this.shift = shift;
            this.hourlyPayRate = hourlyPayRate;
        }

        public int getShift() {
            return shift;
        }

        public void setShift(int shift) {
            this.shift = shift;
        }

        public double getHourlyPayRate() {
            return hourlyPayRate;
        }

        public void setHourlyPayRate(double hourlyPayRate) {
            this.hourlyPayRate = hourlyPayRate;
        }

        @Override
        public String toString() {
            return super.toString() + "\nShift: " + shift + "\nHourly pay rate: " + hourlyPayRate;
        };

    }

    public static void main(String[] args) throws NumberFormatException, IOException {
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
