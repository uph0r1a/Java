import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Ex10 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static interface IStudent {
        public void input() throws IOException;

        public void display();
    }

    public static class Student implements IStudent {
        private int id;
        private String fullname, email;
        private float mark;

        public Student() {
        }

        public Student(int id, String fullname, String email, float mark) {
            this.id = id;
            this.fullname = fullname;
            this.email = email;
            this.mark = mark;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getFullname() {
            return fullname;
        }

        public void setFullname(String fullname) {
            this.fullname = fullname;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public float getMark() {
            return mark;
        }

        public void setMark(float mark) {
            this.mark = mark;
        }

        @Override
        public void input() throws IOException {
            System.out.print("Enter student ID: ");
            int id;
            while (true) {
                try {
                    id = Integer.parseInt(br.readLine().strip());
                    if (id >= 0) {
                        break;
                    }
                    System.out.print("Invalid ID\nRe-enter student ID: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter student ID: ");
                }
            }

            System.out.print("Enter student fullname: ");
            String name = br.readLine().strip();

            System.out.print("Enter student email: ");
            String email = br.readLine().strip();

            System.out.print("Enter student mark: ");
            float mark;
            while (true) {
                try {
                    mark = Float.parseFloat(br.readLine().strip());
                    if (mark >= 0 && mark <= 10) {
                        break;
                    }
                    System.out.print("Invalid mark\nRe-enter student mark: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter student mark: ");
                }
            }

            setId(id);
            setFullname(name);
            setEmail(email);
            setMark(mark);
        }

        @Override
        public void display() {
            System.out.println("\nID: " + getId() + "\nFullname: " + getFullname() + "\nEmail: " + getEmail()
                    + "\nMark: " + getMark());
        }
    }

    public static class StudentManagement {
        private List<Student> students;

        public StudentManagement() {
            students = new ArrayList<>();
        }

        public void addStudent() throws IOException {
            for (int i = 0; i < 3; i++) {
                Student s = new Student();
                s.input();
                students.add(s);
            }
            System.out.println("3 students added successfully.");
        }

        public void showStudent() {
            if (students.isEmpty()) {
                System.out.println("No students added yet.");
                return;
            }
            for (Student student : students) {
                student.display();
            }
        }

        public Student sortStudentByMark() {
            if (students.isEmpty()) {
                System.out.println("No students added yet.");
                return null;
            }
            students.sort(Comparator.comparing(Student::getMark).reversed());
            return students.get(0);
        }
    }

    public static void main(String[] args) throws IOException {
        StudentManagement management = new StudentManagement();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    === MENU ===
                    1. Add New.
                    2. Show All.
                    3. Sort.
                    4. Exit.
                    Your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine().strip());
                    if (choice >= 1 && choice <= 4) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.print("Error: " + e.getMessage() + "\nRe-enter your choice: ");
                }
            }

            switch (choice) {
                case 1 -> management.addStudent();
                case 2 -> management.showStudent();
                case 3 -> {
                    Student top = management.sortStudentByMark();
                    if (top == null) {
                        System.out.println("No students added yet.");
                    } else {
                        System.out.println("Student with the highest score:");
                        top.display();
                    }
                }
                case 4 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}