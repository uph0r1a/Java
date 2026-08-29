import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Ex11 {
    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<Double> grades = new ArrayList<>();
        System.out.print("Enter number of student: ");
        int n;
        while (true) {
            try {
                n = Integer.parseInt(br.readLine());
                if (n >= 0 && n <= 100) {
                    break;
                }
                System.out.print("Invalid number of student\nRe-enter number of student: ");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        for (int i = 0; i < n; i++) {
            System.out.print("Enter grade: ");
            double grade;
            while (true) {
                try {
                    grade = Double.parseDouble(br.readLine());
                    if (grade >= 0 && grade <= 10) {
                        break;
                    }
                    System.out.print("Invalid grade\nRe-enter grade: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            grades.add(grade);
        }

        int count = 0;
        for (int i = 0; i < grades.size() - 1; i++) {
            boolean swap = false;
            for (int j = 0; j < grades.size() - i - 1; j++) {
                if (grades.get(j) < grades.get(j + 1)) {
                    Double temp = grades.get(j);
                    grades.set(j, grades.get(j + 1));
                    grades.set(j + 1, temp);
                    swap = true;
                    count++;
                }
            }
            if (!swap) {
                break;
            }
        }
        System.out.println("Grade list after sort (descending): ");
        for (Double g : grades) {
            System.out.print(g + " ");
        }
        System.out.println("\nHighest score: " + grades.get(0) + "\nLowest score: " + grades.get(grades.size() - 1)
                + "\nNumber of swap: " + count);
    }
}
