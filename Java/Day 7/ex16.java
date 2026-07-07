import java.util.Arrays;

public class ex16 {
    public static <T extends Number> double getTotal(T[][] arr) {
        return Arrays.stream(arr).flatMap(Arrays::stream).mapToDouble(Number::doubleValue).sum();
    }

    public static <T extends Number> double getAverage(T[][] arr) {
        return Arrays.stream(arr).flatMap(Arrays::stream).mapToDouble(Number::doubleValue).average().orElse(-1);
    }

    public static <T extends Number> double getRowTotal(T[][] arr, int row) {
        return Arrays.stream(arr[row]).mapToDouble(Number::doubleValue).sum();
    }

    public static <T extends Number> double getColumnTotal(T[][] arr, int col) {
        return Arrays.stream(arr).mapToDouble(row -> row[col].doubleValue()).sum();
    }

    public static <T extends Number> double getHighestInRow(T[][] arr, int row) {
        return Arrays.stream(arr[row]).mapToDouble(Number::doubleValue).max().orElse(-1);
    }

    public static <T extends Number> double getLowestInRow(T[][] arr, int row) {
        return Arrays.stream(arr[row]).mapToDouble(Number::doubleValue).min().orElse(-1);
    }

    public static void main(String[] args) {
        Integer[][] arr = {
                { 1, 2, 2, 3, 4, 5, 2, 2, 4, 6, 33, 5 },
                { 1, 2, 2, 3, 4, 5, 2, 2, 4, 6, 33, 5 },
                { 1, 2, 2, 3, 4, 5, 2, 2, 4, 6, 33, 5 },
                { 1, 2, 2, 3, 4, 5, 2, 2, 4, 6, 33, 5 }
        };

        System.out.println("Total: " + getTotal(arr) + "\nAverage: " + getAverage(arr) + "\nRow 1 total: "
                + getRowTotal(arr, 0) + "\nColumn 1 total: " + getColumnTotal(arr, 0) + "\nHighest in row 1: "
                + getHighestInRow(arr, 0) + "\nLowest in row 1: " + getLowestInRow(arr, 0));
    }
}
