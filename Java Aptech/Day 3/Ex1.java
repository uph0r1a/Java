import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Ex1 {
    private static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    static public class Book {
        private String isbn, title, author;
        private int yearPublish;
        private double price;

        public Book() {
        }

        public Book(String isbn, String title, String author, int yearPublish, double price) {
            this.isbn = isbn;
            this.title = title;
            this.author = author;
            this.yearPublish = yearPublish;
            this.price = price;
        }

        public String getIsbn() {
            return isbn;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }

        public int getYearPublish() {
            return yearPublish;
        }

        public double getPrice() {
            return price;
        }

        public void setIsbn(String isbn) {
            this.isbn = isbn;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public void setYearPublish(int yearPublish) {
            this.yearPublish = yearPublish;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public void display() {
            System.out.println("ISBN: " + isbn + "\nTitle: " + title + "\nAuthor: " + author + "\nYear Publish: "
                    + yearPublish + "\nPrice: $" + String.format("%.2f", price));
        }

        @Override
        public String toString() {
            return "ISBN: " + isbn + "\nTitle: " + title + "\nAuthor: " + author + "\nYear Publish: " + yearPublish
                    + "\nPrice: $" + String.format("%.2f", price);
        }
    }

    private static boolean isbnExists(List<Book> books, String isbn) {
        for (Book book : books) {
            if (book.getIsbn().equalsIgnoreCase(isbn)) {
                return true;
            }
        }
        return false;
    }

    private static void addBook(List<Book> books) throws IOException {
        String isbn;
        while (true) {
            System.out.print("Enter ISBN: ");
            isbn = br.readLine().trim();

            if (isbnExists(books, isbn)) {
                System.out.println("A book with this ISBN already exists! Please enter a different ISBN.");
            } else {
                break;
            }
        }

        System.out.print("Enter title: ");
        String title = br.readLine().trim();

        System.out.print("Enter author: ");
        String author = br.readLine().trim();

        System.out.print("Enter year publish: ");
        int yearPublish = readInt();

        System.out.print("Enter price: ");
        double price = readDouble();

        books.add(new Book(isbn, title, author, yearPublish, price));

        System.out.println("Book added successfully!");
    }

    private static void displayBooks(List<Book> books) {
        if (books.isEmpty()) {
            System.out.println("No books available.");
            return;
        }

        for (int i = 0; i < books.size(); i++) {
            System.out.println("\n--------------------");
            System.out.println("Book " + (i + 1));
            books.get(i).display();
        }
    }

    private static void searchByISBN(List<Book> books) throws IOException {
        System.out.print("Enter ISBN: ");
        String isbn = br.readLine().trim();

        for (Book book : books) {
            if (book.getIsbn().equalsIgnoreCase(isbn)) {
                System.out.println("\nBook found:");
                book.display();
                return;
            }
        }

        System.out.println("No book found!");
    }

    private static void searchByTitle(List<Book> books) throws IOException {
        System.out.print("Enter title keyword: ");
        String keyword = br.readLine().trim().toLowerCase();

        boolean found = false;

        for (Book book : books) {
            if (book.getTitle().toLowerCase().contains(keyword)) {
                System.out.println("\nBook found:");
                book.display();
                System.out.println("--------------------");
                found = true;
            }
        }

        if (!found) {
            System.out.println("No book found!");
        }
    }

    private static int readInt() throws IOException {
        while (true) {
            try {
                return Integer.parseInt(br.readLine().trim());
            } catch (NumberFormatException e) {
                System.out.print("Invalid number! Enter again: ");
            }
        }
    }

    private static double readDouble() throws IOException {
        while (true) {
            try {
                return Double.parseDouble(br.readLine().trim());
            } catch (NumberFormatException e) {
                System.out.print("Invalid price! Enter again: ");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        List<Book> books = new ArrayList<>();
        boolean isRun = true;

        while (isRun) {
            System.out.print("""
                    ===== Book Management Menu =====
                    1. Add new Book
                    2. Display all books
                    3. Search book by ISBN
                    4. Search book by name
                    5. Exit
                    Choose an option:\s""");

            int choice = readInt();

            switch (choice) {
                case 1 -> addBook(books);
                case 2 -> displayBooks(books);
                case 3 -> searchByISBN(books);
                case 4 -> searchByTitle(books);
                case 5 -> isRun = false;
                default -> System.out.println("Invalid choice! Please choose 1-5.");
            }
        }
    }
}