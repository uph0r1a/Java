import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Ex1 {
    public static class Student {
        private String maSV, hoTen, lop;
        private double diemTrungBinh;

        public Student(String maSV, String hoTen, String lop, double diemTrungBinh) {
            this.maSV = maSV;
            this.hoTen = hoTen;
            this.lop = lop;
            this.diemTrungBinh = diemTrungBinh;
        }

        public String getMaSV() {
            return maSV;
        }

        public void setMaSV(String maSV) {
            this.maSV = maSV;
        }

        public String getHoTen() {
            return hoTen;
        }

        public void setHoTen(String hoTen) {
            this.hoTen = hoTen;
        }

        public String getLop() {
            return lop;
        }

        public void setLop(String lop) {
            this.lop = lop;
        }

        public double getDiemTrungBinh() {
            return diemTrungBinh;
        }

        public void setDiemTrungBinh(double diemTrungBinh) {
            this.diemTrungBinh = diemTrungBinh;
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
                    String lop = br.readLine();

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
                    Student sv = new Student(id, name, lop, score);
                    students.put(sv.getMaSV(), sv);
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
                        System.out.println(
                                "Student ID: " + sv.getMaSV() + "\nStudent name: " + sv.getHoTen() + "\nStudent class: "
                                        + sv.getLop() + "\nStudent average score: " + sv.getDiemTrungBinh());
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
                        sv.setDiemTrungBinh(score);
                        students.put(id, sv);
                    } else {
                        System.out.println("No student yet");
                    }
                }
                case 4 -> {
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

                        students.remove(id);
                    } else {
                        System.out.println("No student yet");
                    }
                }
                case 5 -> {
                    if (!students.isEmpty()) {
                        boolean exist = false;
                        for (Student sv : students.values()) {
                            if (sv.getDiemTrungBinh() >= 8) {
                                exist = true;
                                System.out.println("Student ID: " + sv.getMaSV() + "\nStudent name: " + sv.getHoTen()
                                        + "\nStudent class: " + sv.getLop() + "\nStudent average score: "
                                        + sv.getDiemTrungBinh());
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
                default -> System.out.println("Invalid choice\nRe-enter choice: ");
            }
        }
    }
}