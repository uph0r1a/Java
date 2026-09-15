import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Ex8 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter number of elements: ");
        int n;
        while (true) {
            try {
                n = Integer.parseInt(br.readLine());
                if (n >= 0) {
                    break;
                }
                System.out.print("Invalid number of elements\nRe-enter number of elements: ");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            System.out.print("Enter element " + (i + 1) + ": ");
            while (true) {
                try {
                    arr[i] = Integer.parseInt(br.readLine());
                    break;
                } catch (Exception e) {
                    System.out.print("Invalid number\nRe-enter element " + (i + 1) + ": ");
                }
            }
        }

        if (arr.length == 0) {
            System.out.println("The array is empty, there is no most-frequent element.");
            return;
        }

        Map<Integer, Integer> counter = new HashMap<>();
        for (int i : arr) {
            counter.put(i, counter.getOrDefault(i, 0) + 1);
        }

        int maxCount = Collections.max(counter.values());
        counter.entrySet().stream().filter(entry -> entry.getValue().equals(maxCount)).map(Map.Entry::getKey)
                .collect(Collectors.toList()).forEach(key -> System.out.println(key + " -> " + maxCount));
    }
}