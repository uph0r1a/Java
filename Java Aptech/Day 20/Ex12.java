
import java.io.*;
import java.util.*;
import java.sql.*;

public class Ex12 {

    static class Book implements Serializable {
        private String ISBN;
        private String title;
        private String author;
        private double price;

        public Book() {
        }

        public Book(String ISBN, String title, String author, double price) {
            this.ISBN = ISBN;
            this.title = title;
            this.author = author;
            this.price = price;
        }

        public String getISBN() {
            return ISBN;
        }

        public void setISBN(String ISBN) {
            this.ISBN = ISBN;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        @Override
        public String toString() {
            return "Book{" +
                    "ISBN='" + ISBN + '\'' +
                    ", title='" + title + '\'' +
                    ", author='" + author + '\'' +
                    ", price=" + price +
                    '}';
        }
    }

    static class BookTest {

        private static final BufferedReader br =
            new BufferedReader(new InputStreamReader(System.in));

        private static final String FILE_NAME = "books.dat";

        public static void main(String[] args) {
            try {
                List<Book> books = inputBooks();

                if (!saveBooks(books)) {
                    return;
                }

                List<Book> loadedBooks = readBooks();

                if (loadedBooks == null) {
                    return;
                }

                System.out.println("\n========== ALL BOOKS ==========");
                displayBooks(loadedBooks);

                System.out.println("\n========== BOOKS WITH PRICE < 50 ==========");
                displayBooksUnder50(loadedBooks);

                // Optional MySQL bonus
                System.out.print("\nDo you want to insert books into MySQL? (y/n): ");
                String choice = br.readLine();

                if (choice != null &&
                    (choice.trim().equalsIgnoreCase("y"))) {
                    insertBooksIntoDatabase(loadedBooks);
                }

            } catch (IOException e) {
                System.out.println(
                    "Error reading keyboard input: " + e.getMessage()
                );
            }
        }

        // Input exactly 5 books
        public static List<Book> inputBooks() throws IOException {
            List<Book> books = new ArrayList<>();

            System.out.println("Enter information for 5 books:");

            for (int i = 0; i < 5; i++) {
                System.out.println("\nBook " + (i + 1));

                String ISBN;

                // Validate ISBN
                while (true) {
                    System.out.print("ISBN: ");
                    ISBN = br.readLine();

                    if (ISBN == null) {
                        throw new EOFException("No more keyboard input.");
                    }

                    ISBN = ISBN.trim();

                    if (!ISBN.matches("^\\d{3}-[A-Z]{2}-\\d{4}$")) {
                        System.out.println(
                            "Invalid ISBN. Example: 123-AB-4567"
                        );
                    } else {
                        break;
                    }
                }

                System.out.print("Title: ");
                String title = br.readLine();

                if (title == null) {
                    throw new EOFException("No more keyboard input.");
                }

                System.out.print("Author: ");
                String author = br.readLine();

                if (author == null) {
                    throw new EOFException("No more keyboard input.");
                }

                // Validate price
                double price;

                while (true) {
                    System.out.print("Price: ");
                    String input = br.readLine();

                    if (input == null) {
                        throw new EOFException("No more keyboard input.");
                    }

                    try {
                        price = Double.parseDouble(input.trim());

                        if (!Double.isFinite(price) || price <= 0) {
                            System.out.println(
                                "Price must be a valid number greater than 0."
                            );
                        } else {
                            break;
                        }

                    } catch (NumberFormatException e) {
                        System.out.println("Invalid price. Try again.");
                    }
                }

                books.add(new Book(ISBN, title, author, price));

                System.out.println("Book added successfully.");
            }

            return books;
        }

        // Serialize the book list
        public static boolean saveBooks(List<Book> books) {
            try (ObjectOutputStream oos =
                     new ObjectOutputStream(
                         new FileOutputStream(FILE_NAME))) {

                oos.writeObject(books);

                System.out.println(
                    "\nBooks saved successfully to " + FILE_NAME
                );

                return true;

            } catch (IOException e) {
                System.out.println(
                    "Error saving books: " + e.getMessage()
                );

                return false;
            }
        }

        // Deserialize the book list
        @SuppressWarnings("unchecked")
        public static List<Book> readBooks() {
            try (ObjectInputStream ois =
                     new ObjectInputStream(
                         new FileInputStream(FILE_NAME))) {

                List<Book> books = (List<Book>) ois.readObject();

                System.out.println("Books loaded successfully.");

                return books;

            } catch (IOException e) {
                System.out.println(
                    "Error reading books: " + e.getMessage()
                );

            } catch (ClassNotFoundException e) {
                System.out.println(
                    "Error: Book class could not be found."
                );
            }

            return null;
        }

        // Display all books
        public static void displayBooks(List<Book> books) {
            for (Book book : books) {
                System.out.println(book);
            }
        }

        // Display books with price less than 50
        public static void displayBooksUnder50(List<Book> books) {
            boolean found = false;

            for (Book book : books) {
                if (book.getPrice() < 50) {
                    System.out.println(book);
                    found = true;
                }
            }

            if (!found) {
                System.out.println("No books found with price below 50.");
            }
        }

        // Bonus: Insert all books into MySQL
        public static void insertBooksIntoDatabase(List<Book> books) {

            String serverURL = "jdbc:mysql://localhost:3306/";
            String databaseURL =
                "jdbc:mysql://localhost:3306/bookmanage";

            String username = "root";
            String password = "your_password";

            String createTableSQL =
                "CREATE TABLE IF NOT EXISTS books (" +
                "ISBN VARCHAR(11) PRIMARY KEY, " +
                "title VARCHAR(255), " +
                "author VARCHAR(255), " +
                "price DOUBLE)";

            String insertSQL =
                "INSERT INTO books (ISBN, title, author, price) " +
                "VALUES (?, ?, ?, ?)";

            try {
                // Create the database if it does not exist
                try (Connection connection =
                         DriverManager.getConnection(
                             serverURL, username, password);
                     Statement statement = connection.createStatement()) {

                    statement.executeUpdate(
                        "CREATE DATABASE IF NOT EXISTS bookmanage"
                    );
                }

                // Connect to the bookmanage database
                try (Connection connection =
                         DriverManager.getConnection(
                             databaseURL, username, password);
                     Statement statement = connection.createStatement()) {

                    statement.executeUpdate(createTableSQL);

                    // Insert all books using PreparedStatement
                    try (PreparedStatement ps =
                             connection.prepareStatement(insertSQL)) {

                        for (Book book : books) {
                            ps.setString(1, book.getISBN());
                            ps.setString(2, book.getTitle());
                            ps.setString(3, book.getAuthor());
                            ps.setDouble(4, book.getPrice());

                            ps.addBatch();
                        }

                        ps.executeBatch();

                        System.out.println(
                            "Books inserted into MySQL successfully."
                        );
                    }
                }

            } catch (SQLException e) {
                System.out.println(
                    "Database error: " + e.getMessage()
                );
            }
        }
    }
}
