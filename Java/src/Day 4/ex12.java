import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex12 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        int sale[] = new int[5];
        for (int i = 0; i < 5; i++) {
            System.out.print("Enter today's sales for store " + (i + 1) + ": ");
            sale[i] = Integer.parseInt(br.readLine());
        }

        System.out.println("\nSALES BAR CHART");
        for (int i = 0; i < 5; i++) {
            System.out.println("Store 1: " + "*".repeat(sale[i] / 100));
        }
    }
}
