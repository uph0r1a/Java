import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex4 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        int[] score = new int[3];
        int sum = 0;

        for (int i = 0; i < 3; i++) {
            System.out.print("Enter score " + (i + 1) + ": ");
            score[i] = Integer.parseInt(br.readLine());
            sum += score[i];
        }
        double average = (double) sum / 3;

        System.out.print("Average score: " + average + "\nLetter grade: ");

        if (average >= 90) {
            System.out.println("A");
        } else if (average >= 80) {
            System.out.println("B");
        } else if (average >= 70) {
            System.out.println("C");
        } else if (average >= 60) {
            System.out.println("D");
        } else {
            System.out.println("F");
        }
    }
}
