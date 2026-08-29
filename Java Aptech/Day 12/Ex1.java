import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Ex1 {
    public static class Student {
        private String studentId, fullName, className;
        private double averageScore;

        public Student(String studentId, String fullName, String className, double averageScore) {
            this.studentId = studentId;
            this.fullName = fullName;
            this.className = className;
            this.averageScore = averageScore;
        }

        public String getStudentId() {
            return studentId;
        }

        public void setStudentId(String studentId) {
            this.studentId = studentId;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public double getAverageScore() {
            return averageScore;
        }

        public void setAverageScore(double averageScore) {
            this.averageScore = averageScore;
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String, Student> students = new HashMap<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    1.  Add new student
                    2.  Search student by ID
                    3.  Update average score
                    4.  Delete student by ID
                    5.  Display students with score >= 8.0
                    0.  Exit
                    Enter your choice:\s """);
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 5) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (choice) {
                case 1 -> {
                    System.out.print("Enter student ID: ");
                    String id;
                    while (true) {
                        try {
                            id = br.readLine();
                            if (!students.containsKey(id)) {
                                break;
                            }
                            System.out.print("ID exist\nRe-enter student ID: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter student name: ");
                    String name = br.readLine();

                    System.out.print("Enter student class: ");
                    String className = br.readLine();

                    System.out.print("Enter student average score: ");
                    double score;
                    while (true) {
                        try {
                            score = Double.parseDouble(br.readLine());
                            if (score >= 0 && score <= 10) {
                                break;
                            }
                            System.out.print("Invalid average score\nRe-enter average score: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }
                    Student sv = new Student(id, name, className, score);
                    students.put(sv.getStudentId(), sv);
                }
                case 2 -> {
                    if (!students.isEmpty()) {
                        System.out.print("Enter student ID: ");
                        String id;
                        while (true) {
                            try {
                                id = br.readLine();
                                if (students.containsKey(id)) {
                                    break;
                                }
                                System.out.print("ID dont exist\nRe-enter student ID: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }

                        Student sv = students.get(id);
                        System.out.println("Student ID: " + sv.getStudentId() + "\nStudent name: " + sv.getFullName()
                                + "\nStudent class: " + sv.getClassName() + "\nStudent average score: "
                                + sv.getAverageScore());
                    } else {
                        System.out.println("No student yet");
                    }
                }
                case 3 -> {
                    if (!students.isEmpty()) {
                        System.out.print("Enter student ID: ");
                        String id;
                        while (true) {
                            try {
                                id = br.readLine();
                                if (students.containsKey(id)) {
                                    break;
                                }
                                System.out.print("ID dont exist\nRe-enter student ID: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }

                        System.out.print("Enter student average score: ");
                        double score;
                        while (true) {
                            try {
                                score = Double.parseDouble(br.readLine());
                                if (score >= 0 && score <= 10) {
                                    break;
                                }
                                System.out.print("Invalid average score\nRe-enter average score: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }

                        Student sv = students.get(id);
                        sv.setAverageScore(score);
                    } else {
                        System.out.println("No student yet");
                    }
                }
                case 4 -> {
                    if (!students.isEmpty()) {
                        System.out.print("Enter student ID: ");
                        String id = br.readLine();

                        boolean removed = students.remove(id) != null;
                        if (removed) {
                            System.out.println("Student removed successfully.");
                        } else {
                            System.out.println("No student found with that ID.");
                        }
                    } else {
                        System.out.println("No student yet");
                    }
                }
                case 5 -> {
                    if (!students.isEmpty()) {
                        boolean exist = false;
                        for (Student sv : students.values()) {
                            if (sv.getAverageScore() >= 8) {
                                exist = true;
                                System.out.println("Student ID: " + sv.getStudentId() + "\nStudent name: "
                                        + sv.getFullName() + "\nStudent class: " + sv.getClassName()
                                        + "\nStudent average score: " + sv.getAverageScore());
                            }
                        }
                        if (!exist) {
                            System.out.println("No student have average score >= 8.0");
                        }
                    } else {
                        System.out.println("No student yet");
                    }
                }
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter choice: ");
            }
        }
    }
}