import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Ex6 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static void addContact(Map<String, String> c) throws IOException{
        System.out.println("Enter contact name: ");
        String name = br.readLine();
        
        
    }
    public static void main(String[] args) {
        Map<String, String> contacts = new HashMap<>();
        contacts.put("Alice", "123-4567");
        contacts.put("Bob", "987-6543");
        System.out.println(contacts.get("Alice"));
        contacts.remove("Bob");
    }
}
