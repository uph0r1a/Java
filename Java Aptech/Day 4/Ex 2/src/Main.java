import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import module.Ticket;
import module.Ticket.Priority;
import module.Ticket.Status;

public class Main {
    public static boolean validID(List<Ticket> ticket, int id) {
        for (int i = 0; i < ticket.size(); i++) {
            if (ticket.get(i).getID() == id) {
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
        for (Status p : Status.values()) {
            if (p.name().equals(status)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        boolean isExit = false;
        List<Ticket> ticket = new ArrayList<>();

        while (!isExit) {
            System.out.print(
                    "=== IT SUPPORT TICKET SYSTEM ===\n1. Create new ticket\n2. View all tickets\n3. Update ticket status\n4. Delete ticket\n5. Filter by priority\n0. Exit\nEnter your choice:");
            int choice = Integer.parseInt(br.readLine());

            switch (choice) {
                case 0:
                    isExit = true;
                    break;
                case 1:
                    System.out.print("Enter ID: ");
                    int id;
                    while (true) {
                        id = Integer.parseInt(br.readLine());
                        if (!validID(ticket, id)) {
                            break;
                        }
                        System.out.print("Invalid ID\nRe-enter ID: ");
                    }

                    System.out.print("Enter title: ");
                    String title = br.readLine();

                    System.out.print("Enter reporter name: ");
                    String reporterName = br.readLine();

                    System.out.print("Enter priority: ");
                    String priority;
                    while (true) {
                        priority = br.readLine().toUpperCase();
                        if (validPriority(priority)) {
                            break;
                        }
                        System.out.print("Invalid priority\nRe-enter priority: ");
                    }

                    System.out.print("Enter status: ");
                    String status;
                    while (true) {
                        status = br.readLine().toUpperCase();
                        if (validStatus(status)) {
                            break;
                        }
                        System.out.print("Invalid priority\nRe-enter priority: ");
                    }

                    ticket.add(new Ticket(id, title, reporterName, Priority.valueOf(priority), Status.valueOf(status)));
                    System.out.println("Ticket add successfully");
                    break;
                case 2:
                    if (ticket.isEmpty()) {
                        System.out.println("No ticket");
                    } else {
                        for (int i = 0; i < ticket.size(); i++) {
                            System.out.println("Ticket " + (i + 1) + ticket.get(i).display() + "\n");
                        }
                    }
                    break;
                case 3:
                    if (ticket.isEmpty()) {
                        System.out.println("No ticket");
                    } else {
                        System.out.print("Enter ID: ");
                        while (true) {
                            id = Integer.parseInt(br.readLine());
                            if (validID(ticket, id)) {
                                break;
                            }
                            System.out.print("Invalid ID\nRe-enter ID: ");
                        }

                        System.out.print("Enter status: ");
                        while (true) {
                            status = br.readLine().toUpperCase();
                            if (validStatus(status)) {
                                break;
                            }
                            System.out.print("Invalid priority\nRe-enter priority: ");
                        }

                        for (int i = 0; i < ticket.size(); i++) {
                            if (ticket.get(i).getID() == id) {
                                ticket.get(i).setStatus(status);
                            }
                        }

                        System.out.println("Status change successfully");
                    }
                    break;
                case 4:
                    if (ticket.isEmpty()) {
                        System.out.println("No ticket");
                    } else {
                        System.out.print("Enter ID: ");
                        while (true) {
                            id = Integer.parseInt(br.readLine());
                            if (validID(ticket, id)) {
                                break;
                            }
                            System.out.print("Invalid ID\nRe-enter ID: ");
                        }

                        for (int i = 0; i < ticket.size(); i++) {
                            if (ticket.get(i).getID() == id) {
                                ticket.remove(i);
                            }
                        }

                        System.out.println("Ticket deleted");
                    }
                    break;
                case 5:
                    if (ticket.isEmpty()) {
                        System.out.println("No ticket");
                    } else {
                        System.out.print("Enter priority: ");
                        while (true) {
                            priority = br.readLine().toUpperCase();
                            if (validPriority(priority)) {
                                break;
                            }
                            System.out.print("Invalid priority\nRe-enter priority: ");
                        }
                        for (int i = 0; i < ticket.size(); i++) {
                            if (ticket.get(i).getPriority().toString().equals(priority)) {
                                System.out.println("Ticket " + (i + 1) + ticket.get(i).display() + "\n");
                            }
                        }
                    }
                    break;
                default:
                    System.out.println("Invalid choice");
                    break;
            }
        }
    }
}