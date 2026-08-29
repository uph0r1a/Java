import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Ex3 {
    public static void main(String[] args) {
        ArrayList<Integer> arr = new ArrayList<>();
        arr.add(1);
        arr.add(2);
        arr.add(3);
        arr.add(4);
        arr.add(3);

        System.out.println("ArrayList: " + arr);

        int pos = 2;
        System.out.println("Element at position " + pos + ": " + arr.get(pos));

        arr.add(0, 10);
        System.out.println("After adding 10 at beginning: " + arr);

        arr.add(20);
        System.out.println("After adding 20 at end: " + arr);

        arr.add(3, 30);
        System.out.println("After adding 30 at position 3: " + arr);

        int x = 4;
        System.out.println(arr.contains(x) ? x + " exists" : x + " doesn't exist");

        int valueToCount = 3;
        int count = Collections.frequency(arr, valueToCount);
        System.out.println("Number of " + valueToCount + "s: " + count);

        arr.remove(Integer.valueOf(3));
        System.out.println("After removing value 3: " + arr);

        arr.remove(2);
        System.out.println("After removing position 2: " + arr);

        ArrayList<Integer> even = arr.stream().filter(n -> n % 2 == 0).collect(Collectors.toCollection(ArrayList::new));
        System.out.println("Even elements: " + even);

        ArrayList<Integer> odd = arr.stream().filter(n -> n % 2 != 0).collect(Collectors.toCollection(ArrayList::new));
        System.out.println("Odd elements: " + odd);

        ArrayList<Integer> arr1 = new ArrayList<>();
        arr1.add(100);
        arr1.add(200);

        ArrayList<Integer> merged = new ArrayList<>(arr);
        merged.addAll(arr1);
        System.out.println(
                "Merged list: " + merged + "\nOriginal arr unchanged: " + arr + "\nOriginal arr1 unchanged: " + arr1);

        ArrayList<Integer> freqDemo = new ArrayList<>(List.of(5, 3, 5, 3, 5, 3));
        System.out.println("\nFrequency demo list: " + freqDemo);

        Map.Entry<Integer, Long> result = freqDemo.stream()
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);

        if (result != null) {
            System.out.println("Most frequent element: " + result.getKey() + " (count " + result.getValue() + ")");
        }

        ArrayList<Integer> dupDemo = new ArrayList<>(List.of(1, 2, 2, 3, 1, 4, 3, 5));
        System.out.println("\nBefore removing duplicates: " + dupDemo);

        ArrayList<Integer> deduped = new ArrayList<>(new LinkedHashSet<>(dupDemo));
        System.out.println("After removing duplicates: " + deduped);
    }
}