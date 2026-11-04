
import java.io.*;
import java.util.*;

public class Ex11 {

    static class Employee implements Serializable {
        private String fullName;
        private int salary;
        private String department;

        public Employee() {
        }

        public Employee(String fullName, int salary, String department) {
            this.fullName = fullName;
            this.salary = salary;
            this.department = department;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public int getSalary() {
            return salary;
        }

        public void setSalary(int salary) {
            this.salary = salary;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        @Override
        public String toString() {
            return "Employee{" +
                    "fullName='" + fullName + '\'' +
                    ", salary=" + salary +
                    ", department='" + department + '\'' +
                    '}';
        }
    }

    static class Test {

        private static final String INPUT_FILE = "employee_data.txt";
        private static final String OUTPUT_FILE = "Employees.obj";

        public static void main(String[] args) {
            List<Employee> employees = readEmployees();

            if (employees == null) {
                return;
            }

            displayEmployees(employees);
            saveEmployees(employees);
        }

        // Read and validate employee records
        public static List<Employee> readEmployees() {
            List<Employee> employees = new ArrayList<>();

            try (BufferedReader br =
                     new BufferedReader(new FileReader(INPUT_FILE))) {

                // Skip the header and dashed line
                br.readLine();
                br.readLine();

                String line;

                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("\\|");

                    if (parts.length < 3) {
                        System.out.println(
                            "Invalid record format: " + line
                        );
                        continue;
                    }

                    String fullName = parts[0].trim();
                    String salaryText = parts[1].trim();
                    String department = parts[2].trim();

                    // Validate full name
                    if (fullName.isEmpty()) {
                        System.out.println("Invalid name");
                        continue;
                    }

                    // Validate salary
                    int salary;

                    try {
                        salary = Integer.parseInt(salaryText);

                        if (salary <= 0) {
                            System.out.println(
                                "Salary is not a valid number for that record."
                            );
                            continue;
                        }

                    } catch (NumberFormatException e) {
                        System.out.println(
                            "Salary is not a valid number for that record."
                        );
                        continue;
                    }

                    // Validate department
                    if (department.isEmpty()) {
                        System.out.println("Invalid department");
                        continue;
                    }

                    employees.add(
                        new Employee(fullName, salary, department)
                    );
                }

                return employees;

            } catch (IOException e) {
                System.out.println(
                    "Error reading employee data: " + e.getMessage()
                );
                return null;
            }
        }

        // Display valid employees
        public static void displayEmployees(List<Employee> employees) {
            System.out.println("\n========== EMPLOYEE LIST ==========");

            for (Employee employee : employees) {
                System.out.println(employee);
            }
        }

        // Serialize employees into Employees.obj
        public static void saveEmployees(List<Employee> employees) {
            try (ObjectOutputStream oos =
                     new ObjectOutputStream(
                         new FileOutputStream(OUTPUT_FILE))) {

                oos.writeObject(employees);

                System.out.println(
                    "\nEmployee records saved successfully to "
                    + OUTPUT_FILE
                );

            } catch (IOException e) {
                System.out.println(
                    "Error saving employee data: " + e.getMessage()
                );
            }
        }
    }
}
