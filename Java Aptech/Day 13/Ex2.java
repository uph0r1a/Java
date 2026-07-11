import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.PriorityQueue;

public class Ex2 {
    public enum STATUS {
        PENDING,
        COMPLETED
    }

    public static class SupportTicket {
        private String ticketID, customerName, issueDescription;
        private int priority;
        private STATUS status;

        public SupportTicket(String ticketID, String customerName, String issueDescription, int priority) {
            this.ticketID = ticketID;
            this.customerName = customerName;
            this.issueDescription = issueDescription;
            this.priority = priority;
            this.status = STATUS.PENDING;
        }

        public String getTicketID() {
            return ticketID;
        }

        public void setTicketID(String ticketID) {
            this.ticketID = ticketID;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }

        public String getIssueDescription() {
            return issueDescription;
        }

        public void setIssueDescription(String issueDescription) {
            this.issueDescription = issueDescription;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public STATUS getStatus() {
            return status;
        }

        public void setStatus(STATUS status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return "Ticket ID: " + ticketID + "\nCustomer Name: " + customerName + "\nIssue Description: "
                    + issueDescription + "\nPriority: " + priority + "\nStatus: " + status + "\n";
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        PriorityQueue<SupportTicket> tickets = new PriorityQueue<>(
                (a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    1.  Create new ticket
                    2.  Process highest priority ticket
                    3.  Display all ticket
                    4.  Number of ticket per priority
                    0.  Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 4) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (choice) {
                case 1 -> {
                    System.out.print("Enter ticket ID: ");
                    String id = br.readLine();

                    System.out.print("Enter customer name: ");
                    String name = br.readLine();

                    System.out.print("Enter issue description: ");
                    String desc = br.readLine();

                    System.out.print("Enter priority: ");
                    int priority;
                    while (true) {
                        try {
                            priority = Integer.parseInt(br.readLine());
                            if (priority >= 1 && priority <= 5) {
                                break;
                            }
                            System.out.print("Invalid priority\nRe-enter priority: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    tickets.offer(new SupportTicket(id, name, desc, priority));
                }
                case 2 -> {
                    if (!tickets.isEmpty()) {
                        SupportTicket ticket = tickets.poll();
                        ticket.setStatus(STATUS.COMPLETED);
                        System.out.println("Ticket process completed\nTicket ID: " + ticket.getTicketID()
                                + "\nCustomer name: " + ticket.getCustomerName() + "\nIssue description: "
                                + ticket.getIssueDescription() + "\nPriority: " + ticket.getPriority() + "\nStatus: "
                                + ticket.getStatus().name());
                    } else {
                        System.out.println("Queue is empty");
                    }
                }
                case 3 -> {
                    if (!tickets.isEmpty()) {
                        System.out.println("Ticket in queue: ");
                        PriorityQueue<SupportTicket> temp = new PriorityQueue<>(tickets);
                        while (!temp.isEmpty()) {
                            System.out.println(temp.poll());
                        }
                    } else {
                        System.out.println("Queue is empty");
                    }
                }
                case 4 -> {
                    if (!tickets.isEmpty()) {
                        for (int i = 0; i < 5; i++) {
                            int count = 0;
                            for (SupportTicket supportTicket : tickets) {
                                if (supportTicket.getPriority() == (i + 1)) {
                                    count++;
                                }
                            }
                            System.out.println("Priority " + (i + 1) + ": " + count);
                        }
                    } else {
                        System.out.println("Queue is empty");
                    }
                }
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}