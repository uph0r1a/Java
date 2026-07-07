import java.io.*;
import java.util.Arrays;

public class ex1_2_9 {
    static class TestScores implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final int[] scores;

        public TestScores(int[] scores) throws InvalidTestScore {
            for (int i : scores) {
                if (i < 0 || i > 100) {
                    throw new InvalidTestScore("Score must be from 0-100");
                }
            }
            this.scores = scores;
        }

        public double getAverage() {
            return Arrays.stream(scores).average().orElse(0.0);
        }

        @Override
        public String toString() {
            return "TestScores{scores=" + Arrays.toString(scores) + ", average=" + getAverage() + "}";
        }
    }

    static class InvalidTestScore extends Exception {
        public InvalidTestScore(String message) {
            super(message);
        }
    }

    public static void main(String[] args) throws InvalidTestScore {
        int[] scores = { 10, 20, 30, 40, 50, 60, 70, 80, 90, 100 };
        TestScores[] score = {
                new TestScores(scores),
                new TestScores(scores),
                new TestScores(scores),
                new TestScores(scores),
                new TestScores(scores)
        };

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("files/testScores.ser"))) {
            for (TestScores testScores : score) {
                out.writeObject(testScores);
                System.out.println("Serialized: " + testScores);
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream("files/testScores.ser"))) {
            for (int i = 0; i < score.length; i++) {
                TestScores restored = (TestScores) in.readObject();
                System.out.println("Deserialized: " + restored);
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}