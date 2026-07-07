import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ex12 {
    public static double getTotal(List<Double> arr) {
        return arr.stream().mapToDouble(Double::doubleValue).sum();
    }

    public static double getAverage(List<Double> arr) {
        return arr.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public static double getHighest(List<Double> arr) {
        return arr.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    public static double getLowest(List<Double> arr) {
        return arr.stream().mapToDouble(Double::doubleValue).min().orElse(0);
    }

    public static void main(String[] args) {
        List<Double> arr = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(Path.of("files/Numbers.txt"))) {
                arr.add(Double.parseDouble(line));
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        
        System.out.println("Total: " + getTotal(arr) + "\nAverage: " + getAverage(arr) + "\nMax: " + getHighest(arr)
                + "\nMin: " + getLowest(arr));
    }
}
