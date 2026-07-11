import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;

public class Ex1 {
    public enum TYPE {
        DEPOSIT,
        WITHDRAW,
        BORROW
    }

    public static class Customer {
        private String customerID, name;
        private TYPE serviceType;
        private long arrivalTime;

        public Customer(String customerID, String name, TYPE serviceType, long arrivalTime) {
            this.customerID = customerID;
            this.name = name;
            this.serviceType = serviceType;
            this.arrivalTime = arrivalTime;
        }

        public String getCustomerID() {
            return customerID;
        }

        public void setCustomerID(String customerID) {
            this.customerID = customerID;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public TYPE getServiceType() {
            return serviceType;
        }

        public void setServiceType(TYPE serviceType) {
            this.serviceType = serviceType;
        }

        @Override
        public String toString() {
            return "\nCustomer ID: " + customerID + "\nCustomer Name: " + name + "\nService Type: " + serviceType.name()
                    + "\nArrival Time: " + arrivalTime + "\n";
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Queue<Customer> customers = new LinkedList<>();
        boolean isExit = false;
        while (!isExit) {
            System.out.print("""
                    1.  Add customer to queue
                    2.  Serve customer
                    3.  See next customer in queue
                    4.  Show all customer in queue
                    0.  Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 4) {
                        break;
                    }
                    System.out.println("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            switch (choice) {
                case 1 -> {
                    System.out.print("Enter customer ID: ");
                    String id = br.readLine();

                    System.out.print("Enter customer name: ");
                    String name = br.readLine();

                    System.out.print("1) Deposit \n2) Withdrawal \n3) Borrow\nEnter service type: ");
                    int type;
                    while (true) {
                        try {
                            type = Integer.parseInt(br.readLine());
                            if (type >= 1 && type <= 3) {
                                break;
                            }
                            System.out.print("Invalid choice\nRe-enter service type: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    customers.offer(new Customer(id, name, TYPE.values()[type - 1], System.currentTimeMillis()));
                }
                case 2 -> {
                    if (!customers.isEmpty()) {
                        Customer customer = customers.poll();
                        System.out.println("Serving customer: " + customer.getName() + "\nService: "
                                + customer.getServiceType().name());
                    } else {
                        System.out.println("Queue is empty");
                    }
                }
                case 3 -> {
                    if (!customers.isEmpty()) {
                        System.out.println("Next customer: " + customers.peek());
                    } else {
                        System.out.println("Queue is empty");
                    }
                }
                case 4 -> {
                    System.out.println("Customer in queue: ");
                    customers.forEach(System.out::println);
                }
                case 0 -> isExit = true;
                default -> System.out.println("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}
