import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Ex4 {
    public static void main(String[] args) {
        List<Integer> arr = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        int n = 3;

        System.out.println("Average: " + arr.stream().mapToInt(Integer::intValue).average().orElse(0)
                + "\nSecond largest element: " + arr.stream().sorted(Comparator.reverseOrder()).toList().get(1)
                + "\nNumber of element greater than " + n + ": " + arr.stream().filter(x -> x > n).toList());
    }
}
