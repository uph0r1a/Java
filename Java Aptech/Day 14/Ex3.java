import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Ex3 {
    public static class Student {
        private String name;
        private double grade;

        public Student(String name, double grade) {
            this.name = name;
            this.grade = grade;
        }

        public String getName() {
            return name;
        }

        public double getGrade() {
            return grade;
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<Student> students = new ArrayList<>();
        System.out.print("Enter number of students: ");
        int n;

        while (true) {
            try {
                n = Integer.parseInt(br.readLine());
                if (n > 0 && n <= 100) {
                    break;
                }
                System.out.print("Invalid number. Re-enter: ");
            } catch (Exception e) {
                System.out.print("Error: " + e.getMessage());
            }
        }

        for (int i = 0; i < n; i++) {
            System.out.println("\nStudent " + (i + 1));

            System.out.print("Enter name: ");
            String name = br.readLine();

            System.out.print("Enter grade: ");
            double grade;

            while (true) {
                try {
                    grade = Double.parseDouble(br.readLine());
                    if (grade >= 0 && grade <= 10) {
                        break;
                    }
                    System.out.print("Invalid grade. Re-enter: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage());
                }
            }

            students.add(new Student(name, grade));
        }

        for (int i = 0; i < students.size() - 1; i++) {
            int max = i;

            for (int j = i + 1; j < students.size(); j++) {
                Student s1 = students.get(j);
                Student s2 = students.get(max);

                if (s1.getGrade() > s2.getGrade()
                        || (s1.getGrade() == s2.getGrade() && s1.getName().compareToIgnoreCase(s2.getName()) < 0)) {
                    max = j;
                }
            }

            if (max != i) {
                Student temp = students.get(i);
                students.set(i, students.get(max));
                students.set(max, temp);
            }
        }

        System.out.println("\nRanking after sorting:");
        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);
            System.out.println((i + 1) + ". " + s.getName() + " - " + s.getGrade());
        }

        List<Student> searchList = new ArrayList<>(students);
        for (int i = 0; i < searchList.size() - 1; i++) {
            int min = i;

            for (int j = i + 1; j < searchList.size(); j++) {
                if (searchList.get(j).getName().compareToIgnoreCase(searchList.get(min).getName()) < 0) {
                    min = j;
                }
            }

            if (min != i) {
                Student temp = searchList.get(i);
                searchList.set(i, searchList.get(min));
                searchList.set(min, temp);
            }
        }

        System.out.print("\nEnter name to search: ");
        String key = br.readLine().trim();

        int low = 0, high = searchList.size() - 1;
        boolean found = false;

        while (low <= high) {
            int mid = low + (high - low) / 2, cmp = searchList.get(mid).getName().compareToIgnoreCase(key);
            if (cmp == 0) {

                Student student = searchList.get(mid);

                int rank = -1;

                for (int i = 0; i < students.size(); i++) {
                    if (students.get(i).getName()
                            .equalsIgnoreCase(student.getName())) {
                        rank = i + 1;
                        break;
                    }
                }

                System.out.println("\nFound:\n" + student.getName() + " - " + student.getGrade() + ", Rank: " + rank);

                found = true;
                break;

            } else if (cmp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        if (!found) {
            System.out.println("\nStudent not found.");
        }
    }
}