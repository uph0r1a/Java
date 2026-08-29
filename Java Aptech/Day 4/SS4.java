import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SS4 {
    static public enum Priority {
        LOW,
        MEDIUM,
        HIGH
    }

    static public enum Status {
        OPEN,
        IN_PROGRESS,
        RESOLVED
    }

    static public class Ticket {
        private int id;
        private String title, reporterName;
        private Priority priority;
        private Status status;

        public Ticket() {
            this.id = 0;
            this.title = "";
            this.reporterName = "";
            this.priority = Priority.LOW;
            this.status = Status.OPEN;
        }

        public Ticket(int id, String title, String reporterName, Priority priority) {
            this.id = id;
            this.title = title;
            this.reporterName = reporterName;
            this.priority = priority;
            this.status = Status.OPEN;
        }

        public int getId() {
            return id;
        }

        public Priority getPriority() {
            return priority;
        }

        public void setStatus(String status) {
            this.status = Status.valueOf(status.toUpperCase());
        }

        public void display() {
            System.out.println("\nID: " + id + "\nTitle: " + title + "\nReporter: " + reporterName + "\nPriority: "
                    + priority + "\nStatus: " + status);
        }
    }

    public static boolean validId(List<Ticket> tickets, int id) {
        for (Ticket t : tickets) {
            if (t.getId() == id) {
                return true;
            }
        }
        return false;
    }

    public static boolean validPriority(String priority) {
        for (Priority p : Priority.values()) {
            if (p.name().equals(priority)) {
                return true;
            }
        }
        return false;
    }

    public static boolean validStatus(String status) {
        for (Status s : Status.values()) {
            if (s.name().equals(status)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        List<Ticket> tickets = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    === IT SUPPORT TICKET SYSTEM ===
                    1. Create new ticket
                    2. View all tickets
                    3. Update ticket status
                    4. Delete ticket
                    5. Filter by priority
                    0. Exit
                    Enter your choice:\s""");

            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(sc.nextLine().trim());
                    break;
                } catch (NumberFormatException e) {
                    System.out.print("Invalid choice, please re-enter: ");
                }
            }

            switch (choice) {
                case 0 -> isExit = true;
                case 1 -> {
                    int id;
                    while (true) {
                        System.out.print("Enter ID: ");
                        try {
                            id = Integer.parseInt(sc.nextLine().trim());
                            if (!validId(tickets, id)) {
                                break;
                            }
                            System.out.println("ID already exists.");
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid ID, please enter a number.");
                        }
                    }

                    System.out.print("Enter title: ");
                    String title = sc.nextLine();

                    System.out.print("Enter reporter name: ");
                    String reporterName = sc.nextLine();

                    System.out.println("Available priorities: LOW, MEDIUM, HIGH");
                    String priority;
                    while (true) {
                        System.out.print("Enter priority: ");
                        priority = sc.nextLine().trim().toUpperCase();
                        if (validPriority(priority)) {
                            break;
                        }
                        System.out.println("Invalid priority, please re-enter.");
                    }

                    tickets.add(new Ticket(id, title, reporterName, Priority.valueOf(priority)));
                    System.out.println("Ticket added successfully. Status set to OPEN.");
                }
                case 2 -> {
                    if (tickets.isEmpty()) {
                        System.out.println("No tickets.");
                    } else {
                        for (int i = 0; i < tickets.size(); i++) {
                            System.out.println("\nTicket " + (i + 1));
                            tickets.get(i).display();
                        }
                    }
                }
                case 3 -> {
                    if (tickets.isEmpty()) {
                        System.out.println("No tickets.");
                    } else {
                        int id;
                        while (true) {
                            System.out.print("Enter ID: ");
                            try {
                                id = Integer.parseInt(sc.nextLine().trim());
                                if (validId(tickets, id)) {
                                    break;
                                }
                                System.out.println("Ticket not found.");
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid ID, please enter a number.");
                            }
                        }

                        String status;
                        while (true) {
                            System.out.print("Enter status (OPEN, IN_PROGRESS, RESOLVED): ");
                            status = sc.nextLine().trim().toUpperCase();
                            if (validStatus(status)) {
                                break;
                            }
                            System.out.println("Invalid status, please re-enter.");
                        }

                        for (Ticket t : tickets) {
                            if (t.getId() == id) {
                                t.setStatus(status);
                                break;
                            }
                        }

                        System.out.println("Status changed successfully.");
                    }
                }
                case 4 -> {
                    if (tickets.isEmpty()) {
                        System.out.println("No tickets.");
                    } else {
                        int id;
                        while (true) {
                            System.out.print("Enter ID: ");
                            try {
                                id = Integer.parseInt(sc.nextLine().trim());
                                if (validId(tickets, id)) {
                                    break;
                                }
                                System.out.println("Ticket not found.");
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid ID, please enter a number.");
                            }
                        }

                        final int ticketId = id;
                        tickets.removeIf(t -> t.getId() == ticketId);
                        System.out.println("Ticket deleted.");
                    }
                }
                case 5 -> {
                    if (tickets.isEmpty()) {
                        System.out.println("No tickets.");
                    } else {
                        String priority;
                        while (true) {
                            System.out.print("Enter priority (LOW, MEDIUM, HIGH): ");
                            priority = sc.nextLine().trim().toUpperCase();
                            if (validPriority(priority)) {
                                break;
                            }
                            System.out.println("Invalid priority, please re-enter.");
                        }

                        boolean found = false;
                        for (int i = 0; i < tickets.size(); i++) {
                            if (tickets.get(i).getPriority().name().equals(priority)) {
                                System.out.println("\nTicket " + (i + 1));
                                tickets.get(i).display();
                                found = true;
                            }
                        }
                        if (!found) {
                            System.out.println("No tickets with that priority.");
                        }
                    }
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }
}