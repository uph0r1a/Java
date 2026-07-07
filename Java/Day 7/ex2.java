import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ex2 {
    static class Payroll {
        private final int[] employeeId = { 5658845, 4520125, 7895122, 8777541, 8451277, 1302850, 7580489 };
        private int[] hours;
        private double[] payRate;

        public Payroll() {
        }

        public Payroll(int[] hours, double[] payRate) {
            this.hours = hours;
            this.payRate = payRate;
        }

        public int[] getEmployeeId() {
            return employeeId;
        }

        public int[] getHours() {
            return hours;
        }

        public void setHours(int[] hours) {
            this.hours = hours;
        }

        public double[] getPayRate() {
            return payRate;
        }

        public void setPayRate(double[] payRate) {
            this.payRate = payRate;
        }

        public double getWages(int id) {
            for (int i = 0; i < employeeId.length; i++) {
                if (employeeId[i] == id) {
                    return hours[i] * payRate[i];
                }
            }
            return -1;
        }
    }

    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Payroll p = new Payroll();
        int[] hours = new int[p.getEmployeeId().length];
        double[] rates = new double[p.getEmployeeId().length];

        for (int i = 0; i < p.getEmployeeId().length; i++) {
            System.out.print("Employee " + p.getEmployeeId()[i] + "\nEnter hours: ");
            int hour;
            while (true) {
                try {
                    hour = Integer.parseInt(br.readLine());
                    if (hour >= 0) {
                        break;
                    }
                    System.out.println("Hour cant be negative\nRe-enter hours: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            System.out.print("Enter pay rate: ");
            double rate;
            while (true) {
                try {
                    rate = Double.parseDouble(br.readLine());
                    if (rate >= 6) {
                        break;
                    }
                    System.out.println("Pay rate cant be less than 6.00\nRe-enter pay rate: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            hours[i] = hour;
            rates[i] = rate;
        }
        Payroll payroll = new Payroll(hours, rates);

        for (int i = 0; i < payroll.getEmployeeId().length; i++) {
            System.out.println("\nID: " + payroll.getEmployeeId()[i] + "\nGross wages: "
                    + payroll.getWages(payroll.getEmployeeId()[i]));
        }
    }
}
