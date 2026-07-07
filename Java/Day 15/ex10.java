import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex10 {

    static class Population {
        private final double startingNumber, averageIncrease;
        private final int days;

        public Population(double startingNumber, double averageIncrease, int days) {
            this.startingNumber = startingNumber;
            this.averageIncrease = averageIncrease;
            this.days = days;
        }

        public double getPopulation(int daysElapsed) {
            if (daysElapsed == 0) {
                return startingNumber;
            }
            double previousDay = getPopulation(daysElapsed - 1);
            return previousDay + previousDay * (averageIncrease / 100);
        }

        public void printTable(int currentDay) {
            if (currentDay > days) {
                return;
            }
            System.out.printf("%-5d %-15.2f%n", currentDay, getPopulation(currentDay));
            printTable(currentDay + 1);
        }

        public void printFullReport() {
            System.out.printf("%-5s %-15s%n", "Day", "Population");
            System.out.println("--------------------");
            printTable(1);
        }

        public double getFinalPopulation() {
            return getPopulation(days);
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter starting number of organisms: ");
        int startingNumber = Integer.parseInt(br.readLine());

        System.out.print("Enter average daily population increase (%): ");
        double averageIncrease = Double.parseDouble(br.readLine());

        System.out.print("Enter number of days: ");
        int days = Integer.parseInt(br.readLine());

        Population population = new Population(startingNumber, averageIncrease, days);
        population.printFullReport();

        System.out.printf("%nFinal population after %d days: %.2f%n", days, population.getFinalPopulation());
    }
}