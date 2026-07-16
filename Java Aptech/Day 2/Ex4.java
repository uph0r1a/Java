import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Ex4 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter employee ID: ");
        String id = br.readLine();

        System.out.print("Enter employee name: ");
        String name = br.readLine();

        System.out.print("Enter employee position: ");
        String position = br.readLine();

        System.out.print("Enter employee basic salary: ");
        double salary;
        while (true) {
            try {
                salary = Double.parseDouble(br.readLine());
                if (salary >= 0) {
                    break;
                }
                System.out.print("Invalid basic salary\nRe-enter basic salary: ");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        System.out.print("Enter employee sales done: ");
        double sale;
        while (true) {
            try {
                sale = Double.parseDouble(br.readLine());
                if (sale >= 0) {
                    break;
                }
                System.out.print("Invalid sales done\nRe-enter sales done: ");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        double commission = 0;
        if (sale >= 10000) {
            commission = 30;
        } else if (sale >= 8000) {
            commission = 30;
        } else if (sale >= 6000) {
            commission = 20;
        } else if (sale >= 4000) {
            commission = 10;
        }

        System.out.println("Employee ID: " + id + "\nEmployee Name: " + name + "\nDesignation: " + position
                + "\nBasic Salary: " + salary + "\nSales Done: " + sale + "\nTotal Salary: " + salary
                + salary * ((double) commission / 100));
    }
}
