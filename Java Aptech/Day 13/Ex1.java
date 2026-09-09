import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.Queue;

public class Ex1 {
    public static class Customer {
        private String customerId, name, serviceType;
        private LocalTime arrivalTime;

        public Customer(String customerId, String name, String serviceType, LocalTime arrivalTime) {
            this.customerId = customerId;
            this.name = name;
            this.serviceType = serviceType;
            this.arrivalTime = arrivalTime;
        }

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getServiceType() {
            return serviceType;
        }

        public void setServiceType(String serviceType) {
            this.serviceType = serviceType;
        }

        public LocalTime getArrivalTime() {
            return arrivalTime;
        }

        public void setArrivalTime(LocalTime arrivalTime) {
            this.arrivalTime = arrivalTime;
        }

        @Override
        public String toString() {
            return "\nCustomer ID: " + customerId + "\nCustomer Name: " + name + "\nService Type: " + serviceType
                    + "\nArrival Time: " + arrivalTime + "\n";
        }
    }

    public static void addCustomer(Queue<Customer> customers, String customerId, String name, String serviceType) {
        customers.offer(new Customer(customerId, name, serviceType, LocalTime.now()));
    }

    public static void serveCustomer(Queue<Customer> customers) {
        Customer customer = customers.poll();
        if (customer != null) {
            System.out.println("Serving customer: " + customer.getName() + "\nService: " + customer.getServiceType());
        } else {
            System.out.println("No customers waiting.");
        }
    }

    public static void viewNextCustomer(Queue<Customer> customers) {
        Customer next = customers.peek();
        if (next != null) {
            System.out.println("Next customer: " + next);
        } else {
            System.out.println("No customers waiting.");
        }
    }

    public static void displayWaitingList(Queue<Customer> customers) {
        if (customers.isEmpty()) {
            System.out.println("Queue is empty");
        } else {
            System.out.println("Customers in queue: ");
            customers.forEach(System.out::println);
        }
    }

    public static void main(String[] args) {
        Queue<Customer> customers = new ArrayDeque<>();

        addCustomer(customers, "C001", "Alice", "Deposit");
        addCustomer(customers, "C002", "Bob", "Withdrawal");
        addCustomer(customers, "C003", "Charlie", "Loan");
        addCustomer(customers, "C004", "Diana", "Deposit");

        System.out.println("=== Waiting list after adding customers ===");
        displayWaitingList(customers);

        System.out.println("\n=== View next customer (without serving) ===");
        viewNextCustomer(customers);

        System.out.println("\n=== Serve a customer ===");
        serveCustomer(customers);

        System.out.println("\n=== Waiting list after serving one customer ===");
        displayWaitingList(customers);

        System.out.println("\n=== Serve remaining customers one by one ===");
        serveCustomer(customers);
        serveCustomer(customers);
        serveCustomer(customers);

        System.out.println("\n=== Attempt to serve when queue is empty ===");
        serveCustomer(customers);

        System.out.println("\n=== Attempt to view next when queue is empty ===");
        viewNextCustomer(customers);
    }
}