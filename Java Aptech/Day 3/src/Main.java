import module.Book;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static void main(String[] args) throws IOException {

        List<Book> books = new ArrayList<>();
        boolean isRun = true;

        while (isRun) {
            System.out.println("\n===== Book Management Menu =====");
            System.out.println("1. Add new Book");
            System.out.println("2. Display all books");
            System.out.println("3. Search book by ISBN");
            System.out.println("4. Search book by title");
            System.out.println("5. Exit");
            System.out.print("Choose an option: ");

            int choice = readInt();

            switch (choice) {
                case 1:
                    addBook(books);
                    break;

                case 2:
                    displayBooks(books);
                    break;

                case 3:
                    searchByISBN(books);
                    break;

                case 4:
                    searchByTitle(books);
                    break;

                case 5:
                    isRun = false;
                    System.out.println("Exiting program...");
                    break;

                default:
                    System.out.println("Invalid choice! Please choose 1-5.");
            }
        }
    }

    private static void addBook(List<Book> books) throws IOException {

        System.out.print("Enter ISBN: ");
        String isbn = br.readLine().trim();

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
            System.out.println(books.get(i));
        }
    }

    private static void searchByISBN(List<Book> books) throws IOException {

        System.out.print("Enter ISBN: ");
        String isbn = br.readLine().trim();

        boolean found = false;

        for (Book book : books) {
            if (book.getIsbn().equalsIgnoreCase(isbn)) {
                System.out.println("\nBook found:");
                System.out.println(book);
                found = true;
            }
        }

        if (!found) {
            System.out.println("Book not found!");
        }
    }

    private static void searchByTitle(List<Book> books) throws IOException {

        System.out.print("Enter title keyword: ");
        String keyword = br.readLine().trim().toLowerCase();

        boolean found = false;

        for (Book book : books) {
            if (book.getTitle().toLowerCase().contains(keyword)) {
                System.out.println("\nBook found:");
                System.out.println(book);
                System.out.println("--------------------");
                found = true;
            }
        }

        if (!found) {
            System.out.println("Book not found!");
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
}