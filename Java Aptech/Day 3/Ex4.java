import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Ex4 {
    private static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    private static int readInt(String prompt) throws IOException {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(br.readLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid number! Try again.");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int count = readInt("Enter the number of elements: ");

        List<Integer> arr = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            arr.add(readInt("Enter element " + (i + 1) + ": "));
        }

        int threshold = readInt("Enter the threshold: ");

        double average = arr.stream().mapToInt(Integer::intValue).average().orElse(0);
        System.out.println("\nAverage: " + average);

        if (arr.size() < 2) {
            System.out.println("Second largest element: not enough elements.");
        } else {
            int max = arr.stream().mapToInt(Integer::intValue).max().getAsInt();
            List<Integer> lessThanMax = arr.stream().filter(x -> x < max).toList();

            if (lessThanMax.isEmpty()) {
                System.out.println("Second largest element: all elements are equal, no second largest.");
            } else {
                int secondLargest = lessThanMax.stream().mapToInt(Integer::intValue).max().getAsInt();
                System.out.println("Second largest element: " + secondLargest);
            }
        }

        long greaterCount = arr.stream().filter(x -> x > threshold).count();
        System.out.println("Number of elements greater than " + threshold + ": " + greaterCount);
    }
}