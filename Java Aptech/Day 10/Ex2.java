import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;

public class Ex2 {
    public static abstract class Course {
        private String courseID, courseName, instructor, startDate;
        private int maxStudents;

        public Course() {
        }

        public Course(String courseID, String courseName, String instructor, String startDate, int maxStudents) {
            this.courseID = courseID;
            this.courseName = courseName;
            this.instructor = instructor;
            this.startDate = startDate;
            this.maxStudents = maxStudents;
        }

        public String getCourseID() {
            return courseID;
        }

        public void setCourseID(String courseID) {
            this.courseID = courseID;
        }

        public String getCourseName() {
            return courseName;
        }

        public void setCourseName(String courseName) {
            this.courseName = courseName;
        }

        public String getInstructor() {
            return instructor;
        }

        public void setInstructor(String instructor) {
            this.instructor = instructor;
        }

        public String getStartDate() {
            return startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public int getMaxStudents() {
            return maxStudents;
        }

        public void setMaxStudents(int maxStudents) {
            this.maxStudents = maxStudents;
        }

        public abstract void input(BufferedReader reader) throws IOException;

        public abstract void display();

        public abstract String getCourseType();

        protected static int readInt(BufferedReader reader, String prompt) throws IOException {
            while (true) {
                System.out.print(prompt);
                try {
                    return Integer.parseInt(reader.readLine());
                } catch (NumberFormatException e) {
                    System.out.println("Invalid number, please try again.");
                }
            }
        }

        protected static double readDouble(BufferedReader reader, String prompt) throws IOException {
            while (true) {
                System.out.print(prompt);
                try {
                    return Double.parseDouble(reader.readLine());
                } catch (NumberFormatException e) {
                    System.out.println("Invalid number, please try again.");
                }
            }
        }
    }

    public static interface CostCalculable {
        public double getCostMetric();
    }

    public static class OnlineCourse extends Course implements CostCalculable {
        private String platform;
        private double courseFee;

        public OnlineCourse() {
        }

        public OnlineCourse(String courseID, String courseName, String instructor, String startDate, int maxStudents,
                String platform, double courseFee) {
            super(courseID, courseName, instructor, startDate, maxStudents);
            this.platform = platform;
            this.courseFee = courseFee;
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public double getCourseFee() {
            return courseFee;
        }

        public void setCourseFee(double courseFee) {
            this.courseFee = courseFee;
        }

        @Override
        public void input(BufferedReader reader) throws IOException {
            System.out.print("Enter course ID: ");
            String id = reader.readLine();

            System.out.print("Enter course name: ");
            String name = reader.readLine();

            System.out.print("Enter instructor: ");
            String instructor = reader.readLine();

            System.out.print("Enter start date: ");
            String date = reader.readLine();

            int maxStudents = readInt(reader, "Enter max students: ");

            System.out.print("Enter platform: ");
            String platform = reader.readLine();

            double fee = readDouble(reader, "Enter course fee: ");

            setCourseID(id);
            setCourseName(name);
            setInstructor(instructor);
            setStartDate(date);
            setMaxStudents(maxStudents);
            setPlatform(platform);
            setCourseFee(fee);
        }

        @Override
        public void display() {
            System.out.println("Course ID: " + getCourseID() + "\nCourse name: " + getCourseName() + "\nInstructor: "
                    + getInstructor() + "\nStart date: " + getStartDate() + "\nMax student: " + getMaxStudents()
                    + "\nPlatform: " + getPlatform() + "\nCourse fee: " + getCourseFee() + "\n");
        }

        @Override
        public String getCourseType() {
            return "ONLINE";
        }

        @Override
        public double getCostMetric() {
            return getCourseFee();
        }
    }

    public static class OfflineCourse extends Course implements CostCalculable {
        private String roomName;
        private boolean hasLab;

        public OfflineCourse() {
        }

        public OfflineCourse(String courseID, String courseName, String instructor, String startDate, int maxStudents,
                String roomName, boolean hasLab) {
            super(courseID, courseName, instructor, startDate, maxStudents);
            this.roomName = roomName;
            this.hasLab = hasLab;
        }

        public String getRoomName() {
            return roomName;
        }

        public void setRoomName(String roomName) {
            this.roomName = roomName;
        }

        public boolean isHasLab() {
            return hasLab;
        }

        public void setHasLab(boolean hasLab) {
            this.hasLab = hasLab;
        }

        @Override
        public void input(BufferedReader reader) throws IOException {
            System.out.print("Enter course ID: ");
            String id = reader.readLine();

            System.out.print("Enter course name: ");
            String name = reader.readLine();

            System.out.print("Enter instructor: ");
            String instructor = reader.readLine();

            System.out.print("Enter start date: ");
            String date = reader.readLine();

            int maxStudents = readInt(reader, "Enter max students: ");

            System.out.print("Enter room name: ");
            String roomName = reader.readLine();

            int labChoice = readInt(reader, "Has lab 1)Yes 2)No: ");
            while (labChoice != 1 && labChoice != 2) {
                System.out.println("Invalid choice, please enter 1 or 2.");
                labChoice = readInt(reader, "Re-enter choice: ");
            }

            setCourseID(id);
            setCourseName(name);
            setInstructor(instructor);
            setStartDate(date);
            setMaxStudents(maxStudents);
            setRoomName(roomName);
            setHasLab(labChoice == 1);
        }

        @Override
        public void display() {
            System.out.println("Course ID: " + getCourseID() + "\nCourse name: " + getCourseName() + "\nInstructor: "
                    + getInstructor() + "\nStart date: " + getStartDate() + "\nMax student: " + getMaxStudents()
                    + "\nRoom name: " + getRoomName() + "\nHas lab: " + (isHasLab() ? "Yes" : "No")
                    + "\nEstimated cost: " + calculateEstimatedCost() + "\n");
        }

        public double calculateEstimatedCost() {
            return getMaxStudents() * (isHasLab() ? 200000 : 120000);
        }

        @Override
        public String getCourseType() {
            return "OFFLINE";
        }

        @Override
        public double getCostMetric() {
            return calculateEstimatedCost();
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        ArrayList<Course> courseList = new ArrayList<>();
        boolean isExit = false;
        while (!isExit) {
            System.out.print("""
                    ===== COURSE MANAGEMENT SYSTEM =====
                    1. Add an Online Course
                    2. Add an Offline Course
                    3. Display all Courses
                    4. Display Online Courses (sorted by cost in ascending order)
                    5. Display Offline Courses (sorted by cost in ascending order)
                    6. Exit
                    Your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 1 && choice <= 6) {
                        break;
                    }
                    System.out.println("Invalid choice\nRe-enter choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            switch (choice) {
                case 1 -> {
                    OnlineCourse course = new OnlineCourse();
                    course.input(br);
                    courseList.add(course);
                }
                case 2 -> {
                    OfflineCourse course = new OfflineCourse();
                    course.input(br);
                    courseList.add(course);
                }
                case 3 -> courseList.forEach(Course::display);
                case 4 -> courseList.stream().filter(course -> course.getClass() == OnlineCourse.class)
                        .map(OnlineCourse.class::cast).sorted(Comparator.comparing(OnlineCourse::getCostMetric))
                        .forEach(OnlineCourse::display);
                case 5 -> courseList.stream().filter(course -> course.getClass() == OfflineCourse.class)
                        .map(OfflineCourse.class::cast).sorted(Comparator.comparing(OfflineCourse::getCostMetric))
                        .forEach(OfflineCourse::display);
                case 6 -> isExit = true;
                default -> System.out.println("Invalid choice\nRe-enter choice: ");
            }
        }
    }
}