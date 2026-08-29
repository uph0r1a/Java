import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Ex6 {
    private static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static class Customer {
        private String headOfHousehold, houseNumber, meterId;

        public Customer(String headOfHousehold, String houseNumber, String meterId) {
            this.headOfHousehold = headOfHousehold;
            this.houseNumber = houseNumber;
            this.meterId = meterId;
        }

        public String getHeadOfHousehold() {
            return headOfHousehold;
        }

        public void setHeadOfHousehold(String headOfHousehold) {
            this.headOfHousehold = headOfHousehold;
        }

        public String getHouseNumber() {
            return houseNumber;
        }

        public void setHouseNumber(String houseNumber) {
            this.houseNumber = houseNumber;
        }

        public String getMeterId() {
            return meterId;
        }

        public void setMeterId(String meterId) {
            this.meterId = meterId;
        }

        @Override
        public String toString() {
            return "Head of household: " + headOfHousehold + " | House number: " + houseNumber + " | Meter ID: "
                    + meterId;
        }
    }

    public static class Bill {
        private Customer customer;
        private int previousReading, newReading;
        private double amountDue;

        public Bill(Customer customer, int previousReading, int newReading, double amountDue) {
            this.customer = customer;
            this.previousReading = previousReading;
            this.newReading = newReading;
            this.amountDue = amountDue;
        }

        public Customer getCustomer() {
            return customer;
        }

        public void setCustomer(Customer customer) {
            this.customer = customer;
        }

        public int getPreviousReading() {
            return previousReading;
        }

        public void setPreviousReading(int previousReading) {
            this.previousReading = previousReading;
        }

        public int getNewReading() {
            return newReading;
        }

        public void setNewReading(int newReading) {
            this.newReading = newReading;
        }

        public double getAmountDue() {
            return amountDue;
        }

        public double calculateAmountDue() {
            amountDue = (newReading - previousReading) * 5;
            return amountDue;
        }
    }

    public static class CustomerManager {
        private final List<Customer> customers = new ArrayList<>();

        public List<Customer> getCustomers() {
            return customers;
        }

        private Optional<Customer> findByMeterId(String meterId) {
            return customers.stream().filter(c -> c.getMeterId().equalsIgnoreCase(meterId)).findFirst();
        }

        public void add(Customer customer) {
            customers.add(customer);
        }

        public boolean remove(String meterId) {
            return customers.removeIf(c -> c.getMeterId().equalsIgnoreCase(meterId));
        }

        public boolean edit(String meterId, String newHeadOfHousehold, String newHouseNumber) {
            Optional<Customer> found = findByMeterId(meterId);
            if (found.isEmpty()) {
                return false;
            }
            Customer customer = found.get();
            customer.setHeadOfHousehold(newHeadOfHousehold);
            customer.setHouseNumber(newHouseNumber);
            return true;
        }

        public void display() {
            if (customers.isEmpty()) {
                System.out.println("No customers on file.");
                return;
            }
            for (Customer c : customers) {
                System.out.println(c);
            }
        }
    }

    private static int readInt(String prompt) throws IOException {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(br.readLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid number, please try again.");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        CustomerManager manager = new CustomerManager();
        boolean isRunning = true;

        while (isRunning) {
            System.out.print("""
                    ===== Electric Bill Management =====
                    1. Add customer
                    2. Remove customer (by meter ID)
                    3. Edit customer (by meter ID)
                    4. Display all customers
                    5. Generate bill for a customer
                    6. Exit
                    Choose an option:\s""");

            int choice = readInt("");

            switch (choice) {
                case 1 -> {
                    System.out.print("Enter head of household: ");
                    String head = br.readLine().trim();

                    System.out.print("Enter house number: ");
                    String houseNumber = br.readLine().trim();

                    System.out.print("Enter meter ID: ");
                    String meterId = br.readLine().trim();

                    manager.add(new Customer(head, houseNumber, meterId));
                    System.out.println("Customer added successfully!");
                }
                case 2 -> {
                    System.out.print("Enter meter ID to remove: ");
                    String meterId = br.readLine().trim();

                    if (manager.remove(meterId)) {
                        System.out.println("Customer removed.");
                    } else {
                        System.out.println("No customer found with that meter ID.");
                    }
                }
                case 3 -> {
                    System.out.print("Enter meter ID to edit: ");
                    String meterId = br.readLine().trim();

                    System.out.print("Enter new head of household: ");
                    String newHead = br.readLine().trim();

                    System.out.print("Enter new house number: ");
                    String newHouseNumber = br.readLine().trim();

                    if (manager.edit(meterId, newHead, newHouseNumber)) {
                        System.out.println("Customer updated.");
                    } else {
                        System.out.println("No customer found with that meter ID.");
                    }
                }
                case 4 -> manager.display();
                case 5 -> {
                    System.out.print("Enter meter ID to bill: ");
                    String meterId = br.readLine().trim();

                    Optional<Customer> found = manager.getCustomers().stream()
                            .filter(c -> c.getMeterId().equalsIgnoreCase(meterId)).findFirst();
                    if (found.isEmpty()) {
                        System.out.println("No customer found with that meter ID.");
                        break;
                    }

                    int previousReading = readInt("Enter previous reading: ");
                    int newReading = readInt("Enter new reading: ");

                    Bill bill = new Bill(found.get(), previousReading, newReading, 0);
                    double amount = bill.calculateAmountDue();

                    System.out.println("Amount due for " + found.get().getHeadOfHousehold() + ": $" + amount);
                }
                case 6 -> isRunning = false;
                default -> System.out.println("Invalid choice! Please choose 1-6.");
            }
        }
    }
}