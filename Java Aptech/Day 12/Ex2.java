import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Ex2 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String, Integer> occurrences = new HashMap<>();
        System.out.print("Enter text:");
        String line;
        while ((line = br.readLine()) != null && !line.isEmpty()) {
            String[] words = line.split("[^a-zA-Z0-9]+");

            for (String word : words) {
                word = word.toLowerCase();
                if (word.isEmpty()) {
                    continue;
                }
                occurrences.put(word, occurrences.getOrDefault(word, 0) + 1);
            }
        }

        occurrences.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5)
                .forEach(entry -> System.out.println(entry.getKey() + " => " + entry.getValue()));
    }
}