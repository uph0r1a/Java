import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Ex6 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static void addContact(Map<String, String> c) throws IOException {
        System.out.print("Enter contact name: ");
        String name = br.readLine().strip();

        System.out.print("Enter contact phone number: ");
        String number;
        while (true) {
            number = br.readLine().strip();
            if (!number.isEmpty()) {
                break;
            }
            System.out.print("Phone number cannot be empty\nRe-enter phone number: ");
        }

        c.put(name, number);
        System.out.println("Contact added successfully");
    }

    public static void removeContact(Map<String, String> c) throws IOException {
        if (c.isEmpty()) {
            System.out.println("No contact yet");
        } else {
            System.out.print("Enter contact name: ");
            String name = br.readLine().strip();

            if (c.remove(name) != null) {
                System.out.println("Contact removed successfully");
            } else {
                System.out.println("No contact with this name");
            }
        }
    }

    public static void searchContact(Map<String, String> c) throws IOException {
        if (c.isEmpty()) {
            System.out.println("No contact yet");
        } else {
            System.out.print("Enter contact name: ");
            String name = br.readLine().strip();

            if (c.containsKey(name)) {
                System.out.println("Name: " + name + "\nPhone number: " + c.get(name));
            } else {
                System.out.println("No contact with this name");
            }
        }
    }

    public static void sortContact(Map<String, String> c) {
        if (c.isEmpty()) {
            System.out.println("No contact yet");
            return;
        }

        List<String> sortedNames = c.keySet().stream().sorted().toList();

        System.out.println("Contacts sorted by name:");
        for (String name : sortedNames) {
            System.out.println(name + " -> " + c.get(name));
        }
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> contacts = new HashMap<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    ===== CONTACT LIST =====
                    1. Add contact
                    2. Remove contact
                    3. Search contact
                    4. Sort and display contacts
                    5. Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 1 && choice <= 5) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (choice) {
                case 1 -> addContact(contacts);
                case 2 -> removeContact(contacts);
                case 3 -> searchContact(contacts);
                case 4 -> sortContact(contacts);
                case 5 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}