import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

public class Ex4 {
    static class SupportTicket {
        int ticketId;
        String customerName, issue, priority;

        public SupportTicket(int ticketId, String customerName, String issue, String priority) {
            this.ticketId = ticketId;
            this.customerName = customerName;
            this.issue = issue;
            this.priority = priority;
        }

        @Override
        public String toString() {
            return "Ticket ID: " + ticketId + " | Customer: " + customerName + " | Issue: " + issue + " | Priority: "
                    + priority;
        }
    }

    static Queue<SupportTicket> ticketQueue = new LinkedList<>();
    static Stack<SupportTicket> undoStack = new Stack<>(), redoStack = new Stack<>();

    public static void addTicket(SupportTicket ticket) {
        ticketQueue.offer(ticket);
        System.out.println("Added: " + ticket);
    }

    public static void processNextTicket() {
        if (ticketQueue.isEmpty()) {
            System.out.println("No tickets waiting to be processed.");
            return;
        }

        SupportTicket ticket = ticketQueue.poll();
        undoStack.push(ticket);
        redoStack.clear();

        System.out.println("Processed: " + ticket);
    }

    public static void undoLastAction() {
        if (undoStack.isEmpty()) {
            System.out.println("No action to undo.");
            return;
        }

        SupportTicket ticket = undoStack.pop();
        redoStack.push(ticket);

        Queue<SupportTicket> updatedQueue = new LinkedList<>();
        updatedQueue.offer(ticket);
        updatedQueue.addAll(ticketQueue);

        ticketQueue = updatedQueue;

        System.out.println("Undo: " + ticket);
    }

    public static void redoLastAction() {
        if (redoStack.isEmpty()) {
            System.out.println("No action to redo.");
            return;
        }

        SupportTicket ticket = redoStack.pop();

        if (!ticketQueue.isEmpty() && ticketQueue.peek() == ticket) {
            ticketQueue.poll();
        }

        undoStack.push(ticket);

        System.out.println("Redo: " + ticket);
    }

    public static void displayQueue() {
        System.out.println("\n===== TICKETS WAITING =====");

        if (ticketQueue.isEmpty()) {
            System.out.println("Queue is empty.");
            return;
        }

        for (SupportTicket ticket : ticketQueue) {
            System.out.println(ticket);
        }
    }

    public static void displayHistory() {
        System.out.println("\n===== PROCESSED TICKET HISTORY =====");

        if (undoStack.isEmpty()) {
            System.out.println("No processed tickets.");
            return;
        }

        for (int i = undoStack.size() - 1; i >= 0; i--) {
            System.out.println(undoStack.get(i));
        }
    }

    public static void main(String[] args) {
        SupportTicket t1 = new SupportTicket(1, "Alice", "Cannot log in", "High"),
                t2 = new SupportTicket(2, "Bob", "Payment failed", "Critical"),
                t3 = new SupportTicket(3, "Charlie", "Slow internet", "Low"),
                t4 = new SupportTicket(4, "David", "Account locked", "High"),
                t5 = new SupportTicket(5, "Emma", "Password reset", "Medium");

        System.out.println("===== ADDING TICKETS =====");
        addTicket(t1);
        addTicket(t2);
        addTicket(t3);
        addTicket(t4);
        addTicket(t5);
        displayQueue();

        System.out.println("\n===== PROCESSING TICKETS =====");
        processNextTicket();
        processNextTicket();
        processNextTicket();
        displayQueue();
        displayHistory();

        System.out.println("\n===== UNDO ACTIONS =====");
        undoLastAction();
        undoLastAction();
        displayQueue();
        displayHistory();

        System.out.println("\n===== REDO ACTION =====");
        redoLastAction();
        displayQueue();
        displayHistory();
    }
}
