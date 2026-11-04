import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Ex4 {
    static class Employee implements Serializable {
        private static final long serialVersionUID = 1L;
        private String employeeID, fullName, address;
        private int age;

        public Employee() {
        }

        public Employee(String employeeID, String fullName, String address, int age) {
            this.employeeID = employeeID;
            this.fullName = fullName;
            this.address = address;
            this.age = age;
        }

        public String getEmployeeID() {
            return employeeID;
        }

        public String getFullName() {
            return fullName;
        }

        public String getAddress() {
            return address;
        }

        public int getAge() {
            return age;
        }

        public void setEmployeeID(String employeeID) {
            this.employeeID = employeeID;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public void setAge(int age) {
            this.age = age;
        }

        @Override
        public String toString() {
            return "Employee ID: " + employeeID + ", Full Name: " + fullName + ", Address: " + address + ", Age: "
                    + age;
        }
    }

    static class EmployeeTest {
        private static final String FILE_NAME = "employee.dat", ID_PATTERN = "^[A-Z]{2}\\d{4}[A-Z]{1,2}$";

        public static String readEmployeeID(BufferedReader br) throws IOException {
            while (true) {
                System.out.print("Employee ID: ");
                String id = br.readLine().trim();

                if (id.matches(ID_PATTERN)) {
                    return id;
                }

                System.out.println(
                        "Invalid employee ID! Please try again.\nFormat: 2 uppercase letters + 4 digits + 1 or 2 uppercase letters.");
            }
        }

        public static List<Employee> inputEmployees(BufferedReader br) throws IOException {
            List<Employee> employees = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                System.out.println("\nEnter information for employee " + (i + 1) + ":");

                String id = readEmployeeID(br);

                System.out.print("Full name: ");
                String name = br.readLine().trim();

                System.out.print("Address: ");
                String address = br.readLine().trim();

                int age;
                while (true) {
                    System.out.print("Age: ");
                    try {
                        age = Integer.parseInt(br.readLine().trim());

                        if (age >= 0) {
                            break;
                        }

                        System.out.println("Age cannot be negative.");
                    } catch (NumberFormatException e) {
                        System.out.println("Please enter a valid integer.");
                    }
                }

                employees.add(new Employee(id, name, address, age));
            }

            return employees;
        }

        public static void writeEmployees(List<Employee> employees) {
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(FILE_NAME))) {
                out.writeObject(employees);
                System.out.println("\nEmployees saved successfully to " + FILE_NAME);
            } catch (IOException e) {
                System.out.println("Error writing employee data: " + e.getMessage());
            }
        }

        @SuppressWarnings("unchecked")
        public static List<Employee> readEmployees() {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(FILE_NAME))) {
                List<Employee> employees = (List<Employee>) in.readObject();
                System.out.println("\nEmployees loaded successfully from " + FILE_NAME);

                return employees;
            } catch (FileNotFoundException e) {
                System.out.println("Error: employee.dat was not found.");
            } catch (ClassNotFoundException e) {
                System.out.println("Error: Employee class could not be found.");
            } catch (IOException e) {
                System.out.println("Error reading employee data: " + e.getMessage());
            }

            return new ArrayList<>();
        }

        public static void displayEmployees(List<Employee> employees) {
            System.out.println("\n===== ALL EMPLOYEES =====");

            for (Employee employee : employees) {
                System.out.println(employee);
            }
        }

        public static void displayEmployeesAbove25(List<Employee> employees) {
            System.out.println("\n===== EMPLOYEES OLDER THAN 25 =====");

            boolean found = false;
            for (Employee employee : employees) {
                if (employee.getAge() > 25) {
                    System.out.println(employee);
                    found = true;
                }
            }

            if (!found) {
                System.out.println("No employees older than 25.");
            }
        }
    }

    public static void main(String[] args) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            List<Employee> employees = EmployeeTest.inputEmployees(br);
            EmployeeTest.writeEmployees(employees);

            List<Employee> loadedEmployees = EmployeeTest.readEmployees();
            EmployeeTest.displayEmployees(loadedEmployees);
            EmployeeTest.displayEmployeesAbove25(loadedEmployees);
        } catch (IOException e) {
            System.out.println("Error during keyboard input: " + e.getMessage());
        }
    }
}
