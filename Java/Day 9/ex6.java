import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex6 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        String[] names = { "Harrison, Rose", "James, Jean", "Smith, William", "Smith, Brad" },
                phones = { "555-2234", "555-9098", "555-1785", "555-9224" };

        System.out.print("Enter a name: ");
        String name = br.readLine().trim();

        for (int i = 0; i < names.length; i++) {
            if (names[i].toLowerCase().contains(name.toLowerCase())) {
                System.out.println(names[i] + ": " + phones[i]);
            }
        }
    }
}
