import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Ex7 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static class Student {
        private String fullName, className;
        private int age;

        public Student(String fullName, String className, int age) {
            this.fullName = fullName;
            this.className = className;
            this.age = age;
        }

        public String getFullName() {
            return fullName;
        }

        public String getClassName() {
            return className;
        }

        public int getAge() {
            return age;
        }

        @Override
        public String toString() {
            return "\nFull Name: " + fullName + "\nClass Name: " + className + "\nAge: " + age;
        }
    }

    public static class BorrowCard {
        private String borrowSlipID, bookID;
        private LocalDate borrowDate, dueDate;
        private Student student;

        public BorrowCard(String borrowSlipID, String bookID, LocalDate borrowDate, LocalDate dueDate,
                Student student) {
            this.borrowSlipID = borrowSlipID;
            this.bookID = bookID;
            this.borrowDate = borrowDate;
            this.dueDate = dueDate;
            this.student = student;
        }

        public String getBorrowSlipID() {
            return borrowSlipID;
        }

        public String getBookID() {
            return bookID;
        }

        public LocalDate getBorrowDate() {
            return borrowDate;
        }

        public LocalDate getDueDate() {
            return dueDate;
        }

        public Student getStudent() {
            return student;
        }

        @Override
        public String toString() {
            return "Borrow Slip ID: " + borrowSlipID + "\nBook ID: " + bookID + "\nBorrow Date: " + borrowDate
                    + "\nDue Date: " + dueDate + "\nStudent: " + student;
        }

    }

    public void add(List<BorrowCard> cards) throws IOException {
        System.out.print("Enter borrow slip ID: ");
        String id = br.readLine();

        System.out.print("Enter borrow slip ID: ");
        String id = br.readLine();

        System.out.print("Enter borrow slip ID: ");
        String id = br.readLine();

        System.out.print("Enter borrow slip ID: ");
        String id = br.readLine();

        System.out.print("Enter borrow slip ID: ");
        String id = br.readLine();

        System.out.print("Enter borrow slip ID: ");
        String id = br.readLine();

        System.out.print("Enter borrow slip ID: ");
        String id = br.readLine();
    }

    public void remove(List<BorrowCard> cards) {

    }

    public static void main(String[] args) {
        List<BorrowCard> cards = new ArrayList<>();
    }
}
