import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ex14 {
    public static void main(String[] args) throws IOException {
        List<String> population = new ArrayList<>(Files.readAllLines(Path.of("files/USPopulation.txt")));
        int max = Integer.MIN_VALUE, min = Integer.MAX_VALUE, average;
        try {

            for (int i = 0; i < population.size(); i++) {
                if (i == 0) {
                    continue;
                }
                int increase = Integer.parseInt(population.get(i)) - Integer.parseInt(population.get(i - 1));
                if (increase > max) {
                    max = increase;
                }
                if (increase < min) {
                    min = increase;
                }
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        average = (Integer.parseInt(population.get(population.size() - 1)) - Integer.parseInt(population.get(0)))
                / (1990 - 1950);
        System.out.println(
                "Average annual change: " + average + "\nGreatest increase: " + max + "\nSmallest increase: " + min);
    }
}
