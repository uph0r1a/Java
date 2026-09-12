import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Ex5 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    public static Map<String, Double> employeeSalary = new HashMap<>();

    public static void addEmployee() throws IOException {
        System.out.print("Enter number of employee: ");
        int number;
        while (true) {
            try {
                number = Integer.parseInt(br.readLine().strip());
                if (number >= 6) {
                    break;
                }
                System.out.print("Invalid number of employee\nRe-enter number of employee: ");
            } catch (Exception e) {
                System.out.print("Error: " + e.getMessage() + "\nRe-enter number of employee: ");
            }
        }

        for (int i = 0; i < number; i++) {
            System.out.print("Enter employee's name: ");
            String name = br.readLine().strip();

            System.out.print("Enter employee salary: ");
            Double salary;
            while (true) {
                try {
                    salary = Double.parseDouble(br.readLine().strip());
                    if (salary >= 0) {
                        break;
                    }
                    System.out.print("Invalid salary\nRe-enter employee salary: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter employee salary: ");
                }
            }
            employeeSalary.put(name, salary);
        }
        System.out.println(number + " employees added successfully.");
    }

    public static void searchEmployee() throws IOException {
        if (employeeSalary.isEmpty()) {
            System.out.println("No employee yet");
        } else {
            System.out.print("Enter employee's name: ");
            String name = br.readLine().strip();

            if (employeeSalary.containsKey(name)) {
                System.out.println("\nEmployee name: " + name + "\nEmployee salary: " + employeeSalary.get(name));
            } else {
                System.out.println("Employee not found");
            }
        }
    }

    public static void updateEmployee() throws IOException {
        if (employeeSalary.isEmpty()) {
            System.out.println("No employee yet");
        } else {
            System.out.print("Enter employee's name: ");
            String name = br.readLine().strip();
            if (employeeSalary.containsKey(name)) {
                System.out.print("Enter employee salary: ");
                Double salary;
                while (true) {
                    try {
                        salary = Double.parseDouble(br.readLine().strip());
                        if (salary >= 0) {
                            break;
                        }
                        System.out.print("Invalid salary\nRe-enter employee salary: ");
                    } catch (Exception e) {
                        System.out.print("Error: " + e.getMessage() + "\nRe-enter employee salary: ");
                    }
                }
                employeeSalary.put(name, salary);
                System.out.println("Salary updated successfully.");
            } else {
                System.out.println("Employee not found");
            }
        }
    }

    public static void removeEmployee() throws IOException {
        if (employeeSalary.isEmpty()) {
            System.out.println("No employee yet");
        } else {
            System.out.print("Enter employee name: ");
            String name = br.readLine().strip();
            if (employeeSalary.containsKey(name)) {
                employeeSalary.remove(name);
                System.out.println("Employee removed successfully.");
            } else {
                System.out.println("Employee not found");
            }
        }
    }

    public static void displayEmployee() {
        if (employeeSalary.isEmpty()) {
            System.out.println("No employee yet");
        } else {
            employeeSalary.forEach(
                    (name, salary) -> System.out.println("\nEmployee name: " + name + "\nEmployee salary: " + salary));
        }
    }

    public static void main(String[] args) throws IOException {
        boolean isExit = false;
        while (!isExit) {
            System.out.print("""
                    1) Add employee data (name and salary) for at least 6 employees
                    2) Search for an employee's salary by name
                    3) Update the salary for a given name
                    4) Remove an employee from the map by name
                    5) Print every employee and their salary
                    6) Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine().strip());
                    if (choice >= 1 && choice <= 6) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter your choice: ");
                }
            }

            switch (choice) {
                case 1 -> addEmployee();
                case 2 -> searchEmployee();
                case 3 -> updateEmployee();
                case 4 -> removeEmployee();
                case 5 -> displayEmployee();
                case 6 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}