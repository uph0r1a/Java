import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex9 {
    static class MonthDays {
        private final int month, year;

        private boolean isLeapYear(int year) {
            return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
        }

        public MonthDays(int month, int year) {
            this.month = month;
            this.year = year;
        }

        public int getNumberOfDays() {
            final int[] DAY = { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };
            if (isLeapYear(year)) {
                DAY[1] = 29;
            }
            return DAY[month - 1];
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        int month, year;
        System.out.print("Enter a month: ");
        while (true) {
            try {
                month = Integer.parseInt(br.readLine());
                if (month >= 1 && month <= 12) {
                    break;
                }
                System.out.print("Invalid month\nRe-enter month: ");
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        System.out.print("Enter a year: ");
        year = Integer.parseInt(br.readLine());

        MonthDays monthDays = new MonthDays(month, year);

        System.out.println(monthDays.getNumberOfDays() + " days");
    }
}
