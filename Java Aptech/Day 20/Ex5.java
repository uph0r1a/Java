import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Ex5 {
    static class Student implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name, phone;
        private int age;

        public Student() {
        }

        public Student(String name, int age, String phone) {
            this.name = name;
            this.age = age;
            this.phone = phone;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public String getPhone() {
            return phone;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        @Override
        public String toString() {
            return "Name: " + name + ", Age: " + age + ", Phone: " + phone;
        }
    }

    static class StudentTest {
        private static final String INPUT_FILE = "input.txt", OUTPUT_FILE = "Persons.obj";

        public static List<Student> readStudents() {
            List<Student> students = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(INPUT_FILE))) {
                br.readLine();
                br.readLine();

                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    String[] parts = line.split("\\|");

                    if (parts.length != 3) {
                        System.out.println("Invalid record.");
                        continue;
                    }

                    String name = parts[0].trim(), ageText = parts[1].trim(), phone = parts[2].trim();
                    int age;

                    try {
                        age = Integer.parseInt(ageText);

                        if (age <= 0) {
                            System.out.println("Not a number");
                            continue;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Not a number");
                        continue;
                    }

                    if (name.isEmpty()) {
                        System.out.println("Invalid name.");
                        continue;
                    }

                    if (phone.isEmpty()) {
                        System.out.println("Invalid phone number");
                        continue;
                    }

                    students.add(new Student(name, age, phone));
                }
            } catch (IOException e) {
                System.out.println("Error reading input file: " + e.getMessage());
            }

            return students;
        }

        public static void saveStudents(List<Student> students) {
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(OUTPUT_FILE))) {
                out.writeObject(students);
                System.out.println("Student records saved successfully to " + OUTPUT_FILE);
            } catch (IOException e) {
                System.out.println("Error writing student records: " + e.getMessage());
            }
        }

        public static void displayStudents(List<Student> students) {
            System.out.println("\n===== STUDENT RECORDS =====");

            for (Student student : students) {
                System.out.println(student);
            }
        }
    }

    public static void main(String[] args) {
        List<Student> students = StudentTest.readStudents();
        StudentTest.displayStudents(students);
        StudentTest.saveStudents(students);
    }
}
