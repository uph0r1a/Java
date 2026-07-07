import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class ex8_9 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        String[] names = new String[5], letterGrades = new String[5];
        double[][] testScores = new double[5][4];
        double[] average = new double[5];

        for (int i = 0; i < 5; i++) {
            System.out.print("Enter name: ");
            names[i] = br.readLine();

            for (int j = 0; j < 4; j++) {
                System.out.print("Enter test score " + (j + 1) + ": ");
                while (true) {
                    try {
                        testScores[i][j] = Double.parseDouble(br.readLine());
                        if (testScores[i][j] >= 0 && testScores[i][j] <= 100) {
                            break;
                        }
                        System.out.print("Invalid test score\nRe-enter test score " + (i + 1) + ": ");
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                }
            }
            average[i] = testScores[i].length > 1
                    ? (Arrays.stream(testScores[i]).sum() - Arrays.stream(testScores[i]).min().orElse(0))
                            / (testScores[i].length - 1)
                    : 0;
            if (average[i] >= 90) {
                letterGrades[i] = "A";
            } else if (average[i] >= 80) {
                letterGrades[i] = "B";
            } else if (average[i] >= 70) {
                letterGrades[i] = "C";
            } else if (average[i] >= 60) {
                letterGrades[i] = "D";
            } else {
                letterGrades[i] = "F";
            }
        }

        for (int i = 0; i < 5; i++) {
            System.out.println("Name: " + names[i] + "\nAverage: " + average[i] + "\nLetter grade: " + letterGrades[i]);
        }
    }
}
