import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class ex6 {
    static class TestScores {
        private double[] score;

        public TestScores(double[] score) {
            this.score = score;
        }

        public double[] getScore() {
            return score;
        }

        public void setScore(double[] score) {
            this.score = score;
        }

        public double average() {
            return Arrays.stream(score).sum() / score.length;
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        double[] score = new double[3];

        for (int i = 0; i < 3; i++) {
            System.out.print("Enter score " + (i + 1) + ": ");
            score[i] = Double.parseDouble(br.readLine());
        }

        TestScores scores = new TestScores(score);

        System.out.println("Average: " + scores.average());
    }
}
