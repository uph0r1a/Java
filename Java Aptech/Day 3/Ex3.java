import java.util.ArrayList;
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

        arr.remove(Integer.valueOf(3));
        System.out.println("After removing value 3: " + arr);

        arr.remove(2);
        System.out.println("After removing position 2: " + arr);

        int x = 4;

        System.out.println(arr.contains(x) ? x + " exists" : x + " doesn't exist");

        int count = (int) arr.stream().filter(n -> n == 4).count();

        System.out.println("Number of 4s: " + count);

        List<Integer> even = arr.stream().filter(n -> n % 2 == 0).toList();

        System.out.println("Even elements: " + even);

        List<Integer> odd = arr.stream().filter(n -> n % 2 != 0).toList();

        System.out.println("Odd elements: " + odd);

        ArrayList<Integer> arr1 = new ArrayList<>();
        arr1.add(100);
        arr1.add(200);

        ArrayList<Integer> merged = new ArrayList<>(arr);
        merged.addAll(arr1);

        System.out.println("Merged list: " + merged);

        Map.Entry<Integer, Long> result = arr.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElse(null);

        if (result != null) {
            System.out.println("Most frequent element: " + result.getKey() + "\nCount: " + result.getValue());
        }

        System.out.println("After removing duplicates: " + new ArrayList<>(new LinkedHashSet<>(arr)));
    }
}