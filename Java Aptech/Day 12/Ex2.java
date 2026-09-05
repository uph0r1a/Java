import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Ex2 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String, Integer> occurrences = new HashMap<>();
        System.out.print("Enter text (blank line to finish): ");
        String line;
        while ((line = br.readLine()) != null && !line.isBlank()) {
            String[] words = line.strip().toLowerCase().split("\\W+");

            for (String word : words) {
                if (word.isEmpty()) {
                    continue;
                }
                occurrences.put(word, occurrences.getOrDefault(word, 0) + 1);
            }
        }

        if (occurrences.isEmpty()) {
            System.out.println("No words entered.");
            return;
        }

        occurrences.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5)
                .forEach(entry -> System.out.println(entry.getKey() + " => " + entry.getValue()));
    }
}