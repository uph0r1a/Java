import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex7 {
    public static double calcAverage(double score1, double score2, double score3, double score4, double score5) {
        return (score1 + score2 + score3 + score4 + score5) / 5;
    }

    public static char determineGrade(double score) {
        if (score >= 90) {
            return 'A';
        } else if (score >= 80) {
            return 'B';
        } else if (score >= 70) {
            return 'C';
        } else if (score >= 60) {
            return 'D';
        } else {
            return 'F';
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter score 1: ");
        double score1 = Double.parseDouble(br.readLine());

        System.out.print("Enter score 2: ");
        double score2 = Double.parseDouble(br.readLine());

        System.out.print("Enter score 3: ");
        double score3 = Double.parseDouble(br.readLine());

        System.out.print("Enter score 4: ");
        double score4 = Double.parseDouble(br.readLine());

        System.out.print("Enter score 5: ");
        double score5 = Double.parseDouble(br.readLine());

        System.out.println("Average: " + calcAverage(score1, score2, score3, score4, score5) + "\nLetter grade: "
                + determineGrade(calcAverage(score1, score2, score3, score4, score5)));
    }
}
