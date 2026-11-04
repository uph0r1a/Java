
import java.io.*;
import java.util.*;

public class Ex14 {

    static class Customer implements Serializable {
        private int id;
        private String name;
        private String email;
        private double balance;

        public Customer() {
        }

        public Customer(int id, String name, String email, double balance) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.balance = balance;
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

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public double getBalance() {
            return balance;
        }

        public void setBalance(double balance) {
            this.balance = balance;
        }

        @Override
        public String toString() {
            return "Customer{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", email='" + email + '\'' +
                    ", balance=" + balance +
                    '}';
        }
    }

    static class CustomerTest {

        private static final BufferedReader br =
            new BufferedReader(new InputStreamReader(System.in));

        private static final String FILE_NAME = "customer.dat";

        public static void main(String[] args) {
            try {
                List<Customer> customers = inputCustomers();

                if (!saveCustomers(customers)) {
                    return;
                }

                List<Customer> loadedCustomers = readCustomers();

                if (loadedCustomers == null) {
                    return;
                }

                // Display customers with balance > 1000
                System.out.println(
                    "\n========== CUSTOMERS WITH BALANCE > 1000 =========="
                );

                displayCustomersAbove1000(loadedCustomers);

                // Sort by balance in descending order
                Collections.sort(
                    loadedCustomers,
                    Comparator.comparingDouble(Customer::getBalance).reversed()
                );

                System.out.println(
                    "\n========== CUSTOMERS SORTED BY BALANCE (DESCENDING) =========="
                );

                displayCustomers(loadedCustomers);

            } catch (IOException e) {
                System.out.println(
                    "Error reading keyboard input: " + e.getMessage()
                );
            }
        }

        // Input exactly 5 customers
        public static List<Customer> inputCustomers() throws IOException {
            List<Customer> customers = new ArrayList<>();

            System.out.println("Enter information for 5 customers:");

            for (int i = 0; i < 5; i++) {
                System.out.println("\nCustomer " + (i + 1));

                // Input ID
                int id;

                while (true) {
                    System.out.print("ID: ");
                    String input = br.readLine();

                    if (input == null) {
                        throw new EOFException("No more keyboard input.");
                    }

                    try {
                        id = Integer.parseInt(input.trim());
                        break;
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid ID. Enter an integer.");
                    }
                }

                // Input name
                System.out.print("Name: ");
                String name = br.readLine();

                if (name == null) {
                    throw new EOFException("No more keyboard input.");
                }

                // Input email
                System.out.print("Email: ");
                String email = br.readLine();

                if (email == null) {
                    throw new EOFException("No more keyboard input.");
                }

                // Input balance
                double balance;

                while (true) {
                    System.out.print("Balance: ");
                    String input = br.readLine();

                    if (input == null) {
                        throw new EOFException("No more keyboard input.");
                    }

                    try {
                        balance = Double.parseDouble(input.trim());

                        if (!Double.isFinite(balance)) {
                            System.out.println(
                                "Balance must be a finite number."
                            );
                        } else {
                            break;
                        }

                    } catch (NumberFormatException e) {
                        System.out.println(
                            "Invalid balance. Enter a number."
                        );
                    }
                }

                Customer customer =
                    new Customer(id, name, email, balance);

                customers.add(customer);

                System.out.println("Customer added successfully.");
            }

            return customers;
        }

        // Serialize customer list
        public static boolean saveCustomers(List<Customer> customers) {
            try (ObjectOutputStream oos =
                     new ObjectOutputStream(
                         new FileOutputStream(FILE_NAME))) {

                oos.writeObject(customers);

                System.out.println(
                    "\nCustomers saved successfully to " + FILE_NAME
                );

                return true;

            } catch (IOException e) {
                System.out.println(
                    "Error saving customers: " + e.getMessage()
                );

                return false;
            }
        }

        // Deserialize customer list
        @SuppressWarnings("unchecked")
        public static List<Customer> readCustomers() {
            try (ObjectInputStream ois =
                     new ObjectInputStream(
                         new FileInputStream(FILE_NAME))) {

                List<Customer> customers =
                    (List<Customer>) ois.readObject();

                System.out.println("Customers loaded successfully.");

                return customers;

            } catch (IOException e) {
                System.out.println(
                    "Error reading customers: " + e.getMessage()
                );

            } catch (ClassNotFoundException e) {
                System.out.println(
                    "Error: Customer class could not be found."
                );
            }

            return null;
        }

        // Display all customers
        public static void displayCustomers(List<Customer> customers) {
            for (Customer customer : customers) {
                System.out.println(customer);
            }
        }

        // Display customers with balance greater than 1000
        public static void displayCustomersAbove1000(
                List<Customer> customers) {

            boolean found = false;

            for (Customer customer : customers) {
                if (customer.getBalance() > 1000) {
                    System.out.println(customer);
                    found = true;
                }
            }

            if (!found) {
                System.out.println(
                    "No customers found with balance greater than 1000."
                );
            }
        }
    }
}
