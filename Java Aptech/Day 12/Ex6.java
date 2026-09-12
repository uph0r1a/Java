import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Ex6 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    private static final String PHONE_REGEX = "^(\\+?1)?[2-9]\\d{2}[2-9]\\d{2}\\d{4}$";
    private static final Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);

    public static boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return false;
        }
        String cleanedNumber = phoneNumber.replaceAll("[\\s\\-()]", "");
        
        return PHONE_PATTERN.matcher(cleanedNumber).matches();
    }

    public static void addContact(Map<String, String> c) throws IOException {
        System.out.println("Enter contact name: ");
        String name = br.readLine();

        System.out.println("Enter contact phone number: ");
        String number;
        while (true) {
            try {
                number = br.readLine();
                if (isValidPhoneNumber(number)) {
                    break;
                }
                System.out.println("Invalid phone number\nRe-enter phone number: ");
            } catch (Exception e) {
                System.out.println("Error: "+ e.getMessage());
            }
        }

        c.put(name, number);
    }

    public static void removeContact(Map<String, String> c) throws IOException {
        if (c.isEmpty()) {
            System.out.println("No contact yet");
        } else {
            System.out.println("Enter contact name: ");
            String name = br.readLine();

            if (c.containsKey(name)) {
                c.remove(name);
            } else {
                System.out.println("No contact with this name");
            }
        }
    }

    public static void searchContact(Map<String, String> c) throws IOException{
        if (c.isEmpty()) {
            System.out.println("No contact yet");
        } else {
            System.out.println("Enter contact name: ");
            String name = br.readLine();

            if (c.containsKey(name)) {
                System.out.println("Name: "+ name+ "\nPhone number: "+ c.get(name));
            } else {
                System.out.println("No contact with this name");
            }
        }
    }

    public static void main(String[] args) {
        Map<String, String> contacts = new HashMap<>();
        contacts.put("Alice", "123-4567");
        contacts.put("Bob", "987-6543");
        System.out.println(contacts.get("Alice"));
        contacts.remove("Bob");
    }
}
