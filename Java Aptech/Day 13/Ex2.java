import java.util.Comparator;
import java.util.PriorityQueue;

public class Ex2 {
    public static class SupportTicket {
        private int ticketId;
        private String customerName, issueDescription, status;
        private int priority;

        public SupportTicket(int ticketId, String customerName, String issueDescription, int priority) {
            this.ticketId = ticketId;
            this.customerName = customerName;
            this.issueDescription = issueDescription;
            this.priority = priority;
            this.status = "OPEN";
        }

        public int getTicketId() {
            return ticketId;
        }

        public void setTicketId(int ticketId) {
            this.ticketId = ticketId;
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

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return "Ticket ID: " + ticketId + "\nCustomer Name: " + customerName + "\nIssue Description: "
                    + issueDescription + "\nPriority: " + priority + "\nStatus: " + status + "\n";
        }
    }

    public static void createTicket(PriorityQueue<SupportTicket> tickets, int ticketId, String customerName,
            String issueDescription, int priority) {
        tickets.offer(new SupportTicket(ticketId, customerName, issueDescription, priority));
    }

    public static void processHighestPriority(PriorityQueue<SupportTicket> tickets) {
        SupportTicket ticket = tickets.poll();
        if (ticket == null) {
            System.out.println("Queue is empty");
            return;
        }
        ticket.setStatus("RESOLVED");
        System.out.println("Ticket processed:\n" + ticket);
    }

    public static void displayTicketsInPriorityOrder(PriorityQueue<SupportTicket> tickets,
            Comparator<SupportTicket> comparator) {
        if (tickets.isEmpty()) {
            System.out.println("Queue is empty");
            return;
        }
        PriorityQueue<SupportTicket> copy = new PriorityQueue<>(comparator);
        copy.addAll(tickets);
        System.out.println("Tickets in priority order:");
        while (!copy.isEmpty()) {
            System.out.println(copy.poll());
        }
    }

    public static void countByPriority(PriorityQueue<SupportTicket> tickets) {
        if (tickets.isEmpty()) {
            System.out.println("Queue is empty");
            return;
        }
        int[] counts = new int[5];
        for (SupportTicket ticket : tickets) {
            counts[ticket.getPriority() - 1]++;
        }
        for (int i = 0; i < 5; i++) {
            System.out.println("Priority " + (i + 1) + ": " + counts[i]);
        }
    }

    public static void main(String[] args) {
        Comparator<SupportTicket> byPriorityThenTicketId = Comparator.comparingInt(SupportTicket::getPriority)
                .thenComparingInt(SupportTicket::getTicketId);
        PriorityQueue<SupportTicket> tickets = new PriorityQueue<>(byPriorityThenTicketId);

        createTicket(tickets, 101, "Alice", "Cannot log in", 2);
        createTicket(tickets, 102, "Bob", "Payment failed", 1);
        createTicket(tickets, 103, "Charlie", "Slow performance", 2); // ties with Alice's priority
        createTicket(tickets, 104, "Diana", "Feature request", 5);
        createTicket(tickets, 105, "Evan", "Data missing", 1); // ties with Bob's priority

        System.out.println("=== All tickets in priority order ===");
        displayTicketsInPriorityOrder(tickets, byPriorityThenTicketId);

        System.out.println("\n=== Count of tickets per priority ===");
        countByPriority(tickets);

        System.out.println("\n=== Process highest priority ticket ===");
        processHighestPriority(tickets);

        System.out.println("\n=== Remaining tickets in priority order ===");
        displayTicketsInPriorityOrder(tickets, byPriorityThenTicketId);

        System.out.println("\n=== Count of tickets per priority (after processing) ===");
        countByPriority(tickets);
    }
}