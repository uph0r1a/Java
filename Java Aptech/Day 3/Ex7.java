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

    public static class BorrowCardManager {
        private final List<BorrowCard> cards = new ArrayList<>();

        private boolean slipIdExists(String slipId) {
            return cards.stream().anyMatch(c -> c.getBorrowSlipID().equalsIgnoreCase(slipId));
        }

        public void add() throws IOException {
            String slipId;
            while (true) {
                System.out.print("Enter borrow slip ID: ");
                slipId = br.readLine().trim();
                if (!slipIdExists(slipId)) {
                    break;
                }
                System.out.println("A card with this slip ID already exists!");
            }

            System.out.print("Enter book ID: ");
            String bookId = br.readLine().trim();

            LocalDate borrowDate = readDate("Enter borrow date (yyyy-MM-dd): ");
            LocalDate dueDate = readDate("Enter due date (yyyy-MM-dd): ");

            System.out.print("Enter student full name: ");
            String fullName = br.readLine().trim();

            System.out.print("Enter student class/section: ");
            String className = br.readLine().trim();

            int age = readInt("Enter student age: ");

            Student student = new Student(fullName, className, age);
            cards.add(new BorrowCard(slipId, bookId, borrowDate, dueDate, student));

            System.out.println("Borrow card added successfully!");
        }

        public void remove() throws IOException {
            System.out.print("Enter the slip ID of the card to remove (return the book): ");
            String slipId = br.readLine().trim();

            boolean removed = cards.removeIf(c -> c.getBorrowSlipID().equalsIgnoreCase(slipId));

            if (removed) {
                System.out.println("Book returned, borrow card removed.");
            } else {
                System.out.println("No card found with that slip ID.");
            }
        }

        public void display() {
            if (cards.isEmpty()) {
                System.out.println("No borrow cards on file.");
                return;
            }

            for (BorrowCard card : cards) {
                System.out.println("\n--------------------");
                System.out.println(card);
            }
        }

        private LocalDate readDate(String prompt) throws IOException {
            while (true) {
                System.out.print(prompt);
                try {
                    return LocalDate.parse(br.readLine().trim());
                } catch (Exception e) {
                    System.out.println("Invalid date format, please use yyyy-MM-dd.");
                }
            }
        }

        private int readInt(String prompt) throws IOException {
            while (true) {
                System.out.print(prompt);
                try {
                    return Integer.parseInt(br.readLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("Invalid number, please try again.");
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        BorrowCardManager manager = new BorrowCardManager();
        boolean isRunning = true;

        while (isRunning) {
            System.out.print("""
                    ===== Library Borrow Card Menu =====
                    1. Add borrow card
                    2. Remove borrow card (return book)
                    3. Display all borrow cards
                    4. Exit
                    Choose an option:\s""");

            int choice;
            try {
                choice = Integer.parseInt(br.readLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid choice!");
                continue;
            }

            switch (choice) {
                case 1 -> manager.add();
                case 2 -> manager.remove();
                case 3 -> manager.display();
                case 4 -> isRunning = false;
                default -> System.out.println("Invalid choice! Please choose 1-4.");
            }
        }
    }
}