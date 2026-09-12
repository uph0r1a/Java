import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Ex4 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static interface IEmployee {
        public void input() throws IOException;

        public void display();
    }

    public static class Employee implements IEmployee {
        private int id;
        private String name, department;
        private double salary;

        public Employee() {
        }

        public Employee(int id, String name, String department, double salary) {
            this.id = id;
            this.name = name;
            this.department = department;
            this.salary = salary;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public double getSalary() {
            return salary;
        }

        public void setSalary(double salary) {
            this.salary = salary;
        }

        // Reads a valid integer employee id (retries on non-numeric input only;
        // uniqueness is checked separately by EmployeeManagement, which doesn't
        // have visibility into a single Employee's other siblings).
        public static int readId() throws IOException {
            System.out.print("Enter employee id: ");
            while (true) {
                try {
                    return Integer.parseInt(br.readLine().strip());
                } catch (NumberFormatException e) {
                    System.out.print("Invalid id\nRe-enter employee id: ");
                }
            }
        }

        @Override
        public void input() throws IOException {
            int id = readId();

            System.out.print("Enter employee name: ");
            String name;
            while (true) {
                try {
                    name = br.readLine().strip();
                    if (name.length() >= 5) {
                        break;
                    }
                    System.out.print("Invalid name\nRe-enter employee name: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter employee name: ");
                }
            }

            System.out.print("Enter employee department: ");
            String department = br.readLine().strip();

            System.out.print("Enter employee salary: ");
            double salary;
            while (true) {
                try {
                    salary = Double.parseDouble(br.readLine().strip());
                    if (salary > 0) {
                        break;
                    }
                    System.out.print("Invalid salary\nRe-enter employee salary: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter employee salary: ");
                }
            }
            setId(id);
            setName(name);
            setDepartment(department);
            setSalary(salary);
        }

        @Override
        public void display() {
            System.out.println("\nEmployee id: " + getId() + "\nEmployee name: " + getName() + "\nEmployee department: "
                    + getDepartment() + "\nEmployee salary: " + getSalary());
            calculateTax();
        }

        public void calculateTax() {
            double tax = getSalary() <= 50000 ? getSalary() * 0.1
                    : getSalary() <= 100000 ? getSalary() * 0.2 : getSalary() * 0.3;
            System.out.println("Employee " + getName() + " has an annual tax of $" + tax + ".");
        }
    }

    public static class EmployeeManagement {
        private List<Employee> employees = new ArrayList<>();

        public EmployeeManagement() {
        }

        private boolean isDuplicateId(int id) {
            return employees.stream().anyMatch(employee -> employee.getId() == id);
        }

        public void addEmployee() throws IOException {
            for (int i = 0; i < 3; i++) {
                Employee e = new Employee();
                e.input();

                while (isDuplicateId(e.getId())) {
                    System.out.print("Employee id " + e.getId() + " already exists.\n");
                    e.setId(Employee.readId());
                }

                employees.add(e);
            }
            System.out.println("3 employees added successfully.");
        }

        public void showEmployees() {
            if (employees.isEmpty()) {
                System.out.println("No employee yet");
            } else {
                for (Employee employee : employees) {
                    employee.display();
                }
            }
        }

        public void sortEmployeesBySalary() {
            if (employees.isEmpty()) {
                System.out.println("No employee yet");
            } else {
                employees.sort(Comparator.comparing(Employee::getSalary));
                employees.forEach(Employee::display);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        EmployeeManagement employeeManagement = new EmployeeManagement();
        boolean isExit = false;
        while (!isExit) {
            System.out.print("""
                    === MENU ===
                    1. Add New Employee
                    2. Show All Employees
                    3. Sort Employees by Salary
                    4. Exit
                    Your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine().strip());
                    if (choice >= 1 && choice <= 4) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter your choice: ");
                }
            }
            switch (choice) {
                case 1 -> employeeManagement.addEmployee();
                case 2 -> employeeManagement.showEmployees();
                case 3 -> employeeManagement.sortEmployeesBySalary();
                case 4 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}