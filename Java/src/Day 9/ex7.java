import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex7 {
    public static String moneyToWords(double amount) {
        String[] ones = { "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
                "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen" },
                tens = { "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety" };
        long dollars = (long) amount;
        int cents = (int) Math.round((amount - dollars) * 100);
        String words = convert(dollars, ones, tens);
        return capitalize(words + " and " + cents + " cents");
    }

    private static String convert(long num, String[] ones, String[] tens) {
        if (num == 0)
            return "zero";

        if (num < 20) {
            return ones[(int) num];
        }

        if (num < 100) {
            return tens[(int) num / 10] + (num % 10 != 0 ? " " + ones[(int) num % 10] : "");
        }

        if (num < 1000) {
            return ones[(int) num / 100] + " hundred" + (num % 100 != 0 ? " " + convert(num % 100, ones, tens) : "");
        }

        if (num < 1_000_000) {
            return convert(num / 1000, ones, tens) + " thousand"
                    + (num % 1000 != 0 ? " " + convert(num % 1000, ones, tens) : "");
        }
        return "number too large";
    }

    private static String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter date: ");
        String date = br.readLine();

        System.out.print("Enter payee name: ");
        String name = br.readLine();

        System.out.print("Enter check amount: ");
        double amount = Double.parseDouble(br.readLine());

        System.out.println();

        System.out.printf("%63s%s%nPay to the Order of: %-40s $%.2f%n%s%n", "Date: ", date, name, amount,
                moneyToWords(amount));
    }
}