import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex5 {
    static class Payroll {
        String name;
        int IDNumber, numberHour;
        double payRate;

        public Payroll(String name, int iDNumber, double payRate, int numberHour) {
            this.name = name;
            IDNumber = iDNumber;
            this.payRate = payRate;
            this.numberHour = numberHour;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getIDNumber() {
            return IDNumber;
        }

        public void setIDNumber(int iDNumber) {
            IDNumber = iDNumber;
        }

        public double getPayRate() {
            return payRate;
        }

        public void setPayRate(double payRate) {
            this.payRate = payRate;
        }

        public int getNumberHour() {
            return numberHour;
        }

        public void setNumberHour(int numberHour) {
            this.numberHour = numberHour;
        }

        public double grossPay() {
            return numberHour * payRate;
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter employee name: ");
        String name = br.readLine();

        System.out.print("Enter ID number: ");
        int id = Integer.parseInt(br.readLine());

        System.out.print("Enter hourly pay rate: ");
        double rate = Double.parseDouble(br.readLine());

        System.out.print("Enter number of hours: ");
        int hours = Integer.parseInt(br.readLine());

        Payroll payroll = new Payroll(name, id, rate, hours);

        System.out.println("Gross pay: " + payroll.grossPay());
    }
}
