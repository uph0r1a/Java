import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

public class ex4_5_6 {
    static public class GradedActivity {
        private double score;

        public GradedActivity() {
            score = 0.0;
        }

        public void setScore(double s) {
            score = s;
        }

        public double getScore() {
            return score;
        }

        public char getGrade() {
            char letterGrade;

            if (score >= 90)
                letterGrade = 'A';
            else if (score >= 80)
                letterGrade = 'B';
            else if (score >= 70)
                letterGrade = 'C';
            else if (score >= 60)
                letterGrade = 'D';
            else
                letterGrade = 'F';

            return letterGrade;
        }
    }

    static public interface Relatable {
        public boolean equals(GradedActivity g);

        public boolean isGreater(GradedActivity g);

        public boolean isLess(GradedActivity g);
    }

    static public class FinalExam extends GradedActivity implements Relatable {
        private final int numMissed;
        private final double pointsEach;

        public FinalExam(int questions, int missed) {
            double numericScore;
            numMissed = missed;
            pointsEach = 100.0 / questions;
            numericScore = 100.0 - (missed * pointsEach);
            setScore(numericScore);
        }

        public double getPointsEach() {
            return pointsEach;
        }

        public int getNumMissed() {
            return numMissed;
        }

        public boolean equals(GradedActivity g) {
            return this.getScore() == g.getScore();
        }

        public boolean isGreater(GradedActivity g) {
            return this.getScore() > g.getScore();
        }

        public boolean isLess(GradedActivity g) {
            return this.getScore() < g.getScore();
        }
    }

    static public class PassFailActivity extends GradedActivity {
        private final double minPassingScore;

        public PassFailActivity(double mps) {
            minPassingScore = mps;
        }

        @Override
        public char getGrade() {
            return super.getScore() >= minPassingScore ? 'P' : 'F';
        }
    }

    static public class PassFailExam extends PassFailActivity {
        private final int numMissed;
        private final double pointsEach;

        public PassFailExam(int questions, int missed, double minPassing) {
            super(minPassing);
            double numericScore;
            numMissed = missed;
            pointsEach = 100.0 / questions;
            numericScore = 100.0 - (missed * pointsEach);
            setScore(numericScore);
        }

        public double getPointsEach() {
            return pointsEach;
        }

        public int getNumMissed() {
            return numMissed;
        }
    }

    static class Essay extends GradedActivity {
        private final double grammar, spelling, length, content;

        public Essay(double grammar, double spelling, double correctLength, double content) {
            this.grammar = grammar;
            this.spelling = spelling;
            this.length = correctLength;
            this.content = content;
            setScore(grammar + spelling + length + content);
        }

        public double getGrammar() {
            return grammar;
        }

        public double getSpelling() {
            return spelling;
        }

        public double getLength() {
            return length;
        }

        public double getContent() {
            return content;
        }
    }

    static public interface Analyzable {
        double getAverage();

        GradedActivity getHighest();

        GradedActivity getLowest();
    }

    static public class CourseGrades implements Analyzable {
        private final GradedActivity[] grades;

        public CourseGrades() {
            grades = new GradedActivity[4];
        }

        public void setLab(GradedActivity lab) {
            grades[0] = lab;
        }

        public void setPassFailExam(PassFailExam pfe) {
            grades[1] = pfe;
        }

        public void setEssay(Essay essay) {
            grades[2] = essay;
        }

        public void setFinalExam(FinalExam exam) {
            grades[3] = exam;
        }

        public String toString() {
            String[] labels = { "Lab Activity", "Pass/Fail Exam", "Essay", "Final Exam" };
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < grades.length; i++) {
                sb.append(labels[i]).append(":\n");
                if (grades[i] != null) {
                    sb.append(String.format("  Score: %.2f%n", grades[i].getScore()));
                    sb.append("  Grade: ").append(grades[i].getGrade()).append("\n");
                } else {
                    sb.append("  Not recorded.\n");
                }
            }
            return sb.toString();
        }

        @Override
        public double getAverage() {
            return Arrays.stream(grades).filter(Objects::nonNull).mapToDouble(GradedActivity::getScore).average()
                    .orElse(0.0);
        }

        @Override
        public GradedActivity getHighest() {
            return Arrays.stream(grades).filter(Objects::nonNull)
                    .max(Comparator.comparingDouble(GradedActivity::getScore)).orElse(null);
        }

        @Override
        public GradedActivity getLowest() {
            return Arrays.stream(grades).filter(Objects::nonNull)
                    .min(Comparator.comparingDouble(GradedActivity::getScore)).orElse(null);
        }
    }

    public static void main(String[] args) {
        Essay essay = new Essay(27, 18, 20, 28);
        GradedActivity lab = new GradedActivity();
        PassFailExam passFailExam = new PassFailExam(10, 2, 70);
        FinalExam finalExam = new FinalExam(50, 4);
        CourseGrades student = new CourseGrades();

        System.out.println("Grammar: " + essay.getGrammar() + "\nSpelling: " + essay.getSpelling() + "\nLength: "
                + essay.getLength() + "\nContent: " + essay.getContent() + "\nTotal Score: " + essay.getScore()
                + "\nLetter Grade: " + essay.getGrade() + "\n");

        lab.setScore(88.0);
        student.setLab(lab);
        student.setPassFailExam(passFailExam);
        student.setEssay(essay);
        student.setFinalExam(finalExam);

        System.out.println(student);
        System.out.printf("Average score: %.2f%n", student.getAverage());

        GradedActivity highest = student.getHighest();
        GradedActivity lowest = student.getLowest();

        if (highest != null) {
            System.out.printf("Highest score: %.2f (Grade: %c)%n", highest.getScore(), highest.getGrade());
        }
        if (lowest != null) {
            System.out.printf("Lowest score: %.2f (Grade: %c)%n", lowest.getScore(), lowest.getGrade());
        }
    }
}