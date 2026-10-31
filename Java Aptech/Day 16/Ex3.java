import java.util.ArrayList;
import java.util.List;

public class Ex3 {
    static class Student {
        int id, age;
        String name;
        double score;

        public Student(int id, String name, double score, int age) {
            this.id = id;
            this.name = name;
            this.score = score;
            this.age = age;
        }

        @Override
        public String toString() {
            return String.format("ID: %d | Name: %-10s | Score: %.2f | Age: %d", id, name, score, age);
        }
    }

    public static void selectionSortByScore(List<Student> students) {
        int n = students.size();

        for (int i = 0; i < n - 1; i++) {
            int maxIndex = i;

            for (int j = i + 1; j < n; j++) {
                if (students.get(j).score > students.get(maxIndex).score) {
                    maxIndex = j;
                }
            }

            Student temp = students.get(i);
            students.set(i, students.get(maxIndex));
            students.set(maxIndex, temp);
        }
    }

    public static void bubbleSortByName(List<Student> students) {
        int n = students.size();

        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false;

            for (int j = 0; j < n - 1 - i; j++) {
                if (students.get(j).name.compareToIgnoreCase(students.get(j + 1).name) > 0) {
                    Student temp = students.get(j);
                    students.set(j, students.get(j + 1));
                    students.set(j + 1, temp);

                    swapped = true;
                }
            }

            if (!swapped) {
                break;
            }
        }
    }

    public static Student binarySearchByName(List<Student> students, String targetName) {
        int left = 0, right = students.size() - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2, comparison = students.get(mid).name.compareToIgnoreCase(targetName);

            if (comparison == 0) {
                return students.get(mid);
            } else if (comparison < 0) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        return null;
    }

    public static Student findHighestScore(List<Student> students) {
        if (students.isEmpty()) {
            return null;
        }

        Student highest = students.get(0);
        for (int i = 1; i < students.size(); i++) {
            if (students.get(i).score > highest.score) {
                highest = students.get(i);
            }
        }

        return highest;
    }

    public static Student findLowestScore(List<Student> students) {
        if (students.isEmpty()) {
            return null;
        }

        Student lowest = students.get(0);
        for (int i = 1; i < students.size(); i++) {
            if (students.get(i).score < lowest.score) {
                lowest = students.get(i);
            }
        }

        return lowest;
    }

    public static void displayStudents(List<Student> students) {
        for (Student student : students) {
            System.out.println(student);
        }
    }

    public static void main(String[] args) {
        List<Student> students = new ArrayList<>();

        students.add(new Student(1, "Alice", 8.5, 20));
        students.add(new Student(2, "David", 7.2, 21));
        students.add(new Student(3, "Charlie", 9.1, 19));
        students.add(new Student(4, "Emma", 6.8, 20));
        students.add(new Student(5, "Frank", 8.9, 22));
        students.add(new Student(6, "Bob", 7.5, 19));
        students.add(new Student(7, "Grace", 9.5, 21));
        students.add(new Student(8, "Henry", 5.9, 20));
        students.add(new Student(9, "Ivy", 8.0, 18));
        students.add(new Student(10, "Jack", 7.8, 22));

        System.out.println("===== ORIGINAL STUDENT LIST =====");
        displayStudents(students);

        System.out.println("\n===== SORT BY SCORE (DESCENDING) =====");
        selectionSortByScore(students);
        displayStudents(students);

        System.out.println("\n===== HIGHEST SCORE =====");
        System.out.println(findHighestScore(students));

        System.out.println("\n===== LOWEST SCORE =====");
        System.out.println(findLowestScore(students));

        System.out.println("\n===== SORT BY NAME (ASCENDING) =====");
        bubbleSortByName(students);
        displayStudents(students);

        String targetName = "Grace";
        System.out.println("\n===== BINARY SEARCH =====\nSearching for: " + targetName);
        Student result = binarySearchByName(students, targetName);

        if (result != null) {
            System.out.println("Student found:");
            System.out.println(result);
        } else {
            System.out.println("Student not found.");
        }
    }
}
