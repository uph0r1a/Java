import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ex6 {
    static class DriverExam {
        private final String[] correct = { "B", "D", "A", "A", "C", "A", "B", "A", "C", "D", "B", "C", "D", "A", "D",
                "C", "C", "B", "D", "A" },answer;

        public DriverExam(String[] answer) {
            this.answer = answer;
        }

        public boolean passed() {
            return totalCorrect() >= 15;
        }

        public int totalCorrect() {
            return correct.length - totalIncorrect();
        }

        public int totalIncorrect() {
            return questionsMissed().size();
        }

        public List<String> questionsMissed() {
            List<String> missed = new ArrayList<>();

            for (int i = 0; i < answer.length; i++) {
                if (!answer[i].equalsIgnoreCase(correct[i])) {
                    missed.add((i + 1) + ": " + answer[i]);
                }
            }
            return missed;
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        String[] answer = new String[20];
        for (int i = 0; i < 20; i++) {
            System.out.print("Enter answer for question " + (i + 1) + ": ");

            while (true) {
                try {
                    answer[i] = br.readLine();
                    if (Arrays.stream(new String[] { "A", "B", "C", "D" }).anyMatch(answer[i]::equalsIgnoreCase)) {
                        break;
                    }
                    System.out.print("Only accept the letters A, B, C, or D as answers\nRe-enter answer for question "
                            + (i + 1) + ": ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        }

        DriverExam driverExam = new DriverExam(answer);

        System.out.println(
                "Total correct: " + driverExam.totalCorrect() + "\nTotal incorrect: " + driverExam.totalIncorrect()
                        + (driverExam.totalIncorrect() == 0 ? "" : "\nQuestion missed: " + driverExam.questionsMissed())
                        + (driverExam.passed() ? "\nPassed" : "\nNot passed"));
    }
}
