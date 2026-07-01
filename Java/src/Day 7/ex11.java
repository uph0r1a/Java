import java.util.Arrays;

public class ex11 {
    public static <T extends Number> double getTotal(T[] arr) {
        return Arrays.stream(arr).mapToDouble(Number::doubleValue).sum();
    }

    public static <T extends Number> double getAverage(T[] arr) {
        return Arrays.stream(arr).mapToDouble(Number::doubleValue).average().orElse(0);
    }

    public static <T extends Number> double getHighest(T[] arr) {
        return Arrays.stream(arr).mapToDouble(Number::doubleValue).max().orElse(0);
    }

    public static <T extends Number> double getLowest(T[] arr) {
        return Arrays.stream(arr).mapToDouble(Number::doubleValue).min().orElse(0);
    }

    public static void main(String[] args) {
        Integer[] arr = { 1, 3, 2, 4, 5, 7, 5, 5, 7, 8, 6, 5, 3, 9, 0, 3, 3 };
        System.out.println("Total: " + getTotal(arr) + "\nAverage: " + getAverage(arr) + "\nMax: " + getHighest(arr)
                + "\nMin: " + getLowest(arr));
    }
}
