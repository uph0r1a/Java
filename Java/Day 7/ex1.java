import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class ex1 {
    static class RainFall {
        private final double[] rain;

        public RainFall(double[] rain) {
            this.rain = rain;
        }

        public double totalRainfall() {
            return Arrays.stream(rain).sum();
        }

        public double averageRainfall() {
            return totalRainfall() / 12;
        }

        public int mostRain() {
            int rainMaxMonth = 0;
            double rainMax = 0;
            for (int i = 0; i < rain.length; i++) {
                if (rain[i] > rainMax) {
                    rainMaxMonth = i + 1;
                    rainMax = rain[i];
                }
            }
            return rainMaxMonth;
        }

        public int leastRain() {
            int rainMinMonth = 0;
            double rainMin = 0;
            for (int i = 0; i < rain.length; i++) {
                if (rain[i] < rainMin) {
                    rainMinMonth = i + 1;
                    rainMin = rain[i];
                }
            }
            return rainMinMonth;
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        double[] rain = new double[12];
        for (int i = 0; i < 12; i++) {
            System.out.print("Enter month " + (i + 1) + " rainfall: ");
            while (true) {
                try {
                    rain[i] = Double.parseDouble(br.readLine());
                    if (rain[i] >= 0) {
                        break;
                    }
                    System.out.print("Rainfall cant be negative\nRe-enter rainfall: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        }

        RainFall rainFall = new RainFall(rain);
        System.out.println("\nThe total rainfall for the year: " + rainFall.totalRainfall()
                + "\nThe average monthly rainfall: " + rainFall.averageRainfall() + "\nThe month with the most rain: "
                + rainFall.mostRain() + "\nThe month with the least rain: " + rainFall.leastRain());
    }
}
