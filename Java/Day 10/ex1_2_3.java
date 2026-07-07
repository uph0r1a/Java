import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;

public class ex1_2_3 {
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
            return "Employee name: " + name + "\nEmployee number: " + number + "\nHire date: " + date;
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
            return super.toString() + "\nShift: " + (shift == 1 ? "Day" : "Night") + "\nHourly pay rate: "
                    + hourlyPayRate;
        };

    }

    static class ShiftSupervisor extends Employee {
        private double annualSalary, annualBonus;

        public ShiftSupervisor(String name, String number, LocalDate date, double annualSalary, double annualBonus) {
            super(name, number, date);
            this.annualSalary = annualSalary;
            this.annualBonus = annualBonus;
        }

        public double getAnnualSalary() {
            return annualSalary;
        }

        public void setAnnualSalary(double annualSalary) {
            this.annualSalary = annualSalary;
        }

        public double getAnnualBonus() {
            return annualBonus;
        }

        public void setAnnualBonus(double annualBonus) {
            this.annualBonus = annualBonus;
        }

        @Override
        public String toString() {
            return super.toString() + "\nAnnual salary: " + annualSalary + "\nAnnual bonus: " + annualBonus;
        }

    }

    static class TeamLeader extends ProductionWorker {
        private double monthlyBonus;
        private final int requiredTrainingHour, attendedTrainingHour;

        public TeamLeader(String name, String number, LocalDate date, int shift, double hourlyPayRate,
                          double monthlyBonus, int requiredTrainingHour, int attendedTrainingHour) {
            super(name, number, date, shift, hourlyPayRate);
            this.monthlyBonus = monthlyBonus;
            this.requiredTrainingHour = requiredTrainingHour;
            this.attendedTrainingHour = attendedTrainingHour;
        }

        public double getMonthlyBonus() {
            return monthlyBonus;
        }

        public void setMonthlyBonus(double monthlyBonus) {
            this.monthlyBonus = monthlyBonus;
        }

        @Override
        public String toString() {
            return super.toString() + "\nMonthly bonus: " + monthlyBonus + "\nRequired training hour: "
                    + requiredTrainingHour + "\nAttended training hour: " + attendedTrainingHour;
        }

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

        System.out.print("Enter annual salary: ");
        double salary = Double.parseDouble(br.readLine());

        System.out.print("Enter annual production bonus: ");
        double bonus = Double.parseDouble(br.readLine());

        System.out.print("Enter monthly bonus amount: ");
        double monthlyBonus = Double.parseDouble(br.readLine());

        System.out.print("Enter required number of training hours: ");
        int requiredTrainingHour = Integer.parseInt(br.readLine());

        System.out.print("Enter number of training hours that the team leader has attended: ");
        int attendedTrainingHour = Integer.parseInt(br.readLine());

        ProductionWorker worker = new ProductionWorker(name, number, date, shift, rate);
        ShiftSupervisor supervisor = new ShiftSupervisor(name, number, date, salary, bonus);
        TeamLeader leader = new TeamLeader(name, number, date, shift, rate, monthlyBonus, requiredTrainingHour,
                attendedTrainingHour);

        System.out.println(worker.toString() + "\n" + supervisor.toString() + "\n" + leader.toString());
    }
}
