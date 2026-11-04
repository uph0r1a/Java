
import java.io.*;
import java.util.*;

public class Ex6 {

    // Employee class
    static class Employee implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private String country;
        private int birthYear;
        private int salary;

        // No-argument constructor
        public Employee() {
        }

        // Full-argument constructor
        public Employee(String name, String country,
                int birthYear, int salary) {
            this.name = name;
            this.country = country;
            this.birthYear = birthYear;
            this.salary = salary;
        }

        // Getters
        public String getName() {
            return name;
        }

        public String getCountry() {
            return country;
        }

        public int getBirthYear() {
            return birthYear;
        }

        public int getSalary() {
            return salary;
        }

        // Setters
        public void setName(String name) {
            this.name = name;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public void setBirthYear(int birthYear) {
            this.birthYear = birthYear;
        }

        public void setSalary(int salary) {
            this.salary = salary;
        }

        @Override
        public String toString() {
            return "Name: " + name
                    + ", Country: " + country
                    + ", Birth Year: " + birthYear
                    + ", Salary: " + salary;
        }
    }

    // Personnel Management System
    static class PersonnelManager {

        private static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        private static final ArrayList<Employee> employees = new ArrayList<>();

        // Read non-blank text
        private static String readNonBlank(String prompt)
                throws IOException {

            while (true) {
                System.out.print(prompt);
                String value = br.readLine();

                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }

                System.out.println("This field cannot be blank.");
            }
        }

        // Read an integer
        private static int readInteger(String prompt)
                throws IOException {

            while (true) {
                System.out.print(prompt);

                try {
                    return Integer.parseInt(br.readLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("Please enter a valid integer.");
                }
            }
        }

        // Read a positive salary
        private static int readSalary() throws IOException {

            while (true) {
                int salary = readInteger("Salary: ");

                if (salary > 0) {
                    return salary;
                }

                System.out.println("Salary must be greater than 0.");
            }
        }

        // Option 1: Enter employee information
        public static void addEmployee() throws IOException {

            System.out.println("\n===== ENTER EMPLOYEE INFORMATION =====");

            String name = readNonBlank("Name: ");
            String country = readNonBlank("Country: ");
            int birthYear = readInteger("Birth year: ");
            int salary = readSalary();

            Employee employee = new Employee(
                    name, country, birthYear, salary);

            employees.add(employee);

            System.out.println("Employee added successfully.");
        }

        // Option 2: Sort employees by salary descending
        public static void sortBySalary() {

            System.out.println("\n===== EMPLOYEES SORTED BY SALARY =====");

            if (employees.isEmpty()) {
                System.out.println("No employees have been entered yet.");
                return;
            }

            // Sort a copy so the original list remains unchanged
            ArrayList<Employee> sortedEmployees = new ArrayList<>(employees);

            // Bubble Sort - descending order
            for (int i = 0; i < sortedEmployees.size() - 1; i++) {
                for (int j = 0; j < sortedEmployees.size() - 1 - i; j++) {

                    if (sortedEmployees.get(j).getSalary() < sortedEmployees.get(j + 1).getSalary()) {

                        Employee temp = sortedEmployees.get(j);

                        sortedEmployees.set(
                                j, sortedEmployees.get(j + 1));

                        sortedEmployees.set(j + 1, temp);
                    }
                }
            }

            for (Employee employee : sortedEmployees) {
                System.out.println(employee);
            }
        }

        // Option 3: Analyze employees by country
        public static void analyzeByCountry() {

            System.out.println("\n===== EMPLOYEES BY COUNTRY =====");

            if (employees.isEmpty()) {
                System.out.println("No employees have been entered yet.");
                return;
            }

            Map<String, Integer> countryCount = new HashMap<>();

            for (Employee employee : employees) {
                String country = employee.getCountry();

                countryCount.put(
                        country,
                        countryCount.getOrDefault(country, 0) + 1);
            }

            for (Map.Entry<String, Integer> entry : countryCount.entrySet()) {

                System.out.println(
                        entry.getKey() + ": " + entry.getValue()
                                + " employee(s)");
            }
        }

        // Option 4: Find employees by country and minimum salary
        public static void findByCountryAndSalary()
                throws IOException {

            System.out.println(
                    "\n===== FIND EMPLOYEES BY COUNTRY AND SALARY =====");

            if (employees.isEmpty()) {
                System.out.println("No employees have been entered yet.");
                return;
            }

            String country = readNonBlank("Enter country: ");
            int minSalary = readInteger("Enter minimum salary: ");

            boolean found = false;

            for (Employee employee : employees) {

                if (employee.getCountry().equalsIgnoreCase(country)
                        && employee.getSalary() >= minSalary) {

                    System.out.println(employee);
                    found = true;
                }
            }

            if (!found) {
                System.out.println("No matching employees found.");
            }
        }

        // Option 5: Save employees to a file
        public static void saveToFile() throws IOException {

            System.out.println("\n===== SAVE EMPLOYEES TO FILE =====");

            String fileName = readNonBlank("Enter filename: ");

            try (ObjectOutputStream out = new ObjectOutputStream(
                    new FileOutputStream(fileName))) {

                out.writeObject(employees);

                System.out.println(
                        "Employees saved successfully to " + fileName);

            } catch (IOException e) {
                System.out.println(
                        "Error saving employee data: " + e.getMessage());
            }
        }

        @SuppressWarnings("unchecked")
        public static void openFile() throws IOException {

            System.out.println("\n===== OPEN EMPLOYEE FILE =====");

            String fileName = readNonBlank("Enter filename: ");

            try (ObjectInputStream in = new ObjectInputStream(
                    new FileInputStream(fileName))) {

                ArrayList<Employee> loadedEmployees = (ArrayList<Employee>) in.readObject();

                System.out.println("\n===== LOADED EMPLOYEES =====");

                if (loadedEmployees.isEmpty()) {
                    System.out.println("The file contains no employees.");
                    return;
                }

                for (Employee employee : loadedEmployees) {
                    System.out.println(employee);
                }

            } catch (FileNotFoundException e) {
                System.out.println("Error: File does not exist.");

            } catch (ClassNotFoundException e) {
                System.out.println(
                        "Error: Employee class could not be found.");

            } catch (IOException e) {
                System.out.println(
                        "Error reading employee file: " + e.getMessage());
            }
        }

        public static void displayMenu() {

            System.out.println("\n========== PERSONNEL MANAGEMENT SYSTEM ==========");
            System.out.println("1. Enter employee information");
            System.out.println("2. Sort by salary (descending)");
            System.out.println("3. Analyze by home region");
            System.out.println("4. Find by region + minimum salary");
            System.out.println("5. Save to file");
            System.out.println("6. Open file and display");
            System.out.println("7. Exit");
            System.out.println("==================================================");
        }

        public static void run() throws IOException {

            int choice;

            do {
                displayMenu();
                choice = readInteger("Enter your choice: ");

                switch (choice) {

                    case 1:
                        addEmployee();
                        break;

                    case 2:
                        sortBySalary();
                        break;

                    case 3:
                        analyzeByCountry();
                        break;
                    case 4:
                        findByCountryAndSalary();
                        break;
                    case 5:
                        saveToFile();
                        break;
                    case 6:
                        openFile();
                        break;
                    case 7:
                        System.out.println("Exiting program...");
                        break;
                    default:
                        System.out.println("Invalid choice! Please select 1 to 7.");
                }
            } while (choice != 7);
        }
    }

    public static void main(String[] args) {
        try {
            PersonnelManager.run();
        } catch (IOException e) {
            System.out.println("Input error: " + e.getMessage());
        }
    }
}
