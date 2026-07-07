import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ex11 {
    public static void main(String[] args) {
        List<Double> totalSalesEach = new ArrayList<>(), averageSalesEach = new ArrayList<>();
        double totalSalesAll = 0, averageSalesAll = 0, max = Double.MIN_VALUE, min = Double.MAX_VALUE;
        int maxWeek = 0, minWeek = 0, count = 0;

        try {
            for (String line : Files.readAllLines(Path.of("files/SalesData.txt"))) {
                count++;
                double[] num = Arrays.stream(line.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                        .mapToDouble(Double::parseDouble).toArray();
                double weeklyTotal = Arrays.stream(num).sum();
                totalSalesEach.add(weeklyTotal);
                averageSalesEach.add(weeklyTotal / 7);
                totalSalesAll += weeklyTotal;

                if (weeklyTotal > max) {
                    max = weeklyTotal;
                    maxWeek = count;
                }

                if (weeklyTotal < min) {
                    min = weeklyTotal;
                    minWeek = count;
                }
            }
            totalSalesAll = totalSalesEach.stream().mapToDouble(Double::doubleValue).sum();
            averageSalesAll = averageSalesEach.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        System.out.print("The total sales for each week: ");
        for (Double salesEach : totalSalesEach) {
            System.out.print(String.format("%.2f", salesEach) + " ");
        }

        System.out.print("\nThe average daily sales for each week: ");
        for (Double salesEach : averageSalesEach) {
            System.out.print(String.format("%.2f", salesEach) + " ");
        }

        System.out.println("\nThe total sales for all of the weeks: " + totalSalesAll + "\nThe average weekly sales: "
                + String.format("%.2f", averageSalesAll) + "\nThe week number that had the highest amount of sales: "
                + maxWeek + "\nThe week number that had the lowest amount of sales: " + minWeek);
    }
}
