import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex10 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        double[] score = new double[3];
        double sum = 0;

        for (int i = 0; i < 3; i++) {
            System.out.print("Enter score " + (i + 1) + ": ");
            score[i] = Double.parseDouble(br.readLine());
            sum += score[i];
        }

        for (int i = 0; i < 3; i++) {
            System.out.println("Score " + (i + 1) + ": " + score[i]);
        }
        System.out.println("Sum: " + sum);
    }
}
