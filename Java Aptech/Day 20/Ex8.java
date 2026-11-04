
import java.io.*;
import java.util.*;
import java.sql.*;

public class Ex8 {

    // Book class
    static class Book {
        private String id;
        private String title;
        private String author;
        private int publishedYear;
        private List<String> genre;

        // Default constructor
        public Book() {
        }

        // Full-argument constructor
        public Book(String id, String title, String author,
                    int publishedYear, List<String> genre) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.publishedYear = publishedYear;
            this.genre = genre;
        }

        // Getters
        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }

        public int getPublishedYear() {
            return publishedYear;
        }

        public List<String> getGenre() {
            return genre;
        }

        // Setters
        public void setId(String id) {
            this.id = id;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public void setPublishedYear(int publishedYear) {
            this.publishedYear = publishedYear;
        }

        public void setGenre(List<String> genre) {
            this.genre = genre;
        }

        @Override
        public String toString() {
            return "ID: " + id
                    + ", Title: " + title
                    + ", Author: " + author
                    + ", Published Year: " + publishedYear
                    + ", Genre: " + String.join(", ", genre);
        }
    }

    // Test class
    static class Test {

        private static final String FILE_NAME = "library_data.txt";

        // Read non-empty text
        private static String readNonBlank(
                BufferedReader br, String prompt) throws IOException {

            while (true) {
                System.out.print(prompt);
                String value = br.readLine();

                if (value == null) {
                    throw new EOFException("Input ended unexpectedly.");
                }

                if (!value.trim().isEmpty()) {
                    return value.trim();
                }

                System.out.println("This field cannot be blank.");
            }
        }

        // Read and validate title
        private static String readTitle(BufferedReader br)
                throws IOException {

            while (true) {
                String title = readNonBlank(br, "Title: ");

                if (title.length() >= 5) {
                    return title;
                }

                System.out.println(
                        "Title must contain at least 5 characters."
                );
            }
        }

        // Read and validate published year
        private static int readPublishedYear(BufferedReader br)
                throws IOException {

            while (true) {
                System.out.print("Published year: ");

                String input = br.readLine();

                if (input == null) {
                    throw new EOFException("Input ended unexpectedly.");
                }

                try {
                    int year = Integer.parseInt(input.trim());

                    if (year > 1900) {
                        return year;
                    }

                    System.out.println(
                            "Published year must be greater than 1900."
                    );

                } catch (NumberFormatException e) {
                    System.out.println(
                            "Please enter a valid integer."
                    );
                }
            }
        }

        // Read genres separated by semicolons
        private static List<String> readGenres(BufferedReader br)
                throws IOException {

            while (true) {
                System.out.print(
                        "Genres (separated by semicolons): "
                );

                String input = br.readLine();

                if (input == null) {
                    throw new EOFException("Input ended unexpectedly.");
                }

                List<String> genres = new ArrayList<>();

                for (String item : input.split(";")) {
                    if (!item.trim().isEmpty()) {
                        genres.add(item.trim());
                    }
                }

                if (!genres.isEmpty()) {
                    return genres;
                }

                System.out.println(
                        "Please enter at least one genre."
                );
            }
        }

        // Input exactly 5 books
        public static List<Book> inputBooks(BufferedReader br)
                throws IOException {

            List<Book> books = new ArrayList<>();

            for (int i = 0; i < 5; i++) {

                System.out.println(
                        "\n===== ENTER BOOK " + (i + 1) + " ====="
                );

                String id = readNonBlank(br, "Book ID: ");
                String title = readTitle(br);
                String author = readNonBlank(br, "Author: ");
                int year = readPublishedYear(br);
                List<String> genres = readGenres(br);

                books.add(
                        new Book(id, title, author, year, genres)
                );
            }

            return books;
        }

        // Save books to a text file
        public static void saveBooks(List<Book> books) {

            try (BufferedWriter bw = new BufferedWriter(
                    new FileWriter(FILE_NAME))) {

                for (Book book : books) {

                    String line = book.getId() + "|"
                            + book.getTitle() + "|"
                            + book.getAuthor() + "|"
                            + book.getPublishedYear() + "|"
                            + String.join(";", book.getGenre());

                    bw.write(line);
                    bw.newLine();
                }

                System.out.println(
                        "\nBooks saved successfully to " + FILE_NAME
                );

            } catch (IOException e) {
                System.out.println(
                        "Error saving books: " + e.getMessage()
                );
            }
        }

        // Read books back from the text file
        public static List<Book> readBooks() {

            List<Book> books = new ArrayList<>();

            try (BufferedReader br = new BufferedReader(
                    new FileReader(FILE_NAME))) {

                String line;

                while ((line = br.readLine()) != null) {

                    String[] parts = line.split("\\|", -1);

                    if (parts.length != 5) {
                        System.out.println(
                                "Skipping invalid record: " + line
                        );
                        continue;
                    }

                    String id = parts[0].trim();
                    String title = parts[1].trim();
                    String author = parts[2].trim();
                    int year = Integer.parseInt(parts[3].trim());

                    List<String> genres = new ArrayList<>();

                    for (String genre : parts[4].split(";")) {
                        if (!genre.trim().isEmpty()) {
                            genres.add(genre.trim());
                        }
                    }

                    books.add(
                            new Book(id, title, author, year, genres)
                    );
                }

                System.out.println(
                        "\nBooks loaded successfully from " + FILE_NAME
                );

            } catch (IOException e) {
                System.out.println(
                        "Error reading books: " + e.getMessage()
                );

            } catch (NumberFormatException e) {
                System.out.println(
                        "Error: Invalid published year in the file."
                );
            }

            return books;
        }

        // Display all books
        public static void displayBooks(List<Book> books) {

            System.out.println("\n===== LIBRARY BOOK RECORDS =====");

            if (books.isEmpty()) {
                System.out.println("No books available.");
                return;
            }

            for (Book book : books) {
                System.out.println(book);
            }
        }

        // Search by title or author
        public static void searchBooks(
                BufferedReader br, List<Book> books)
                throws IOException {

            System.out.println("\n===== SEARCH BOOKS =====");

            String keyword = readNonBlank(
                    br, "Enter title or author to search: "
            );

            boolean found = false;

            for (Book book : books) {

                if (book.getTitle().toLowerCase()
                        .contains(keyword.toLowerCase())
                        || book.getAuthor().toLowerCase()
                        .contains(keyword.toLowerCase())) {

                    System.out.println(book);
                    found = true;
                }
            }

            if (!found) {
                System.out.println("NOT FOUND");
            }
        }

        // Optional MySQL bonus
        public static void saveToMySQL(List<Book> books) {

            // Change these values to match your MySQL configuration.
            final String username = "root";
            final String password = "your_password";

            final String serverURL =
                    "jdbc:mysql://localhost:3306/";

            final String databaseURL =
                    "jdbc:mysql://localhost:3306/library";

            // Create database first, then connect to it.
            try (Connection connection = DriverManager.getConnection(
                    serverURL, username, password);
                 Statement statement = connection.createStatement()) {

                statement.executeUpdate(
                        "CREATE DATABASE IF NOT EXISTS library"
                );

                System.out.println(
                        "Database 'library' is ready."
                );

            } catch (SQLException e) {
                System.out.println(
                        "Error creating database: " + e.getMessage()
                );
                return;
            }

            // Create table and insert books.
            String createTableSQL =
                    "CREATE TABLE IF NOT EXISTS books ("
                    + "id VARCHAR(50) PRIMARY KEY, "
                    + "title VARCHAR(255) NOT NULL, "
                    + "author VARCHAR(255) NOT NULL, "
                    + "publishedYear INT NOT NULL, "
                    + "genre TEXT"
                    + ")";

            String insertSQL =
                    "INSERT INTO books "
                    + "(id, title, author, publishedYear, genre) "
                    + "VALUES (?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "title = VALUES(title), "
                    + "author = VALUES(author), "
                    + "publishedYear = VALUES(publishedYear), "
                    + "genre = VALUES(genre)";

            try (Connection connection = DriverManager.getConnection(
                    databaseURL, username, password);
                 Statement statement = connection.createStatement()) {

                statement.executeUpdate(createTableSQL);

                try (PreparedStatement ps =
                        connection.prepareStatement(insertSQL)) {

                    for (Book book : books) {

                        ps.setString(1, book.getId());
                        ps.setString(2, book.getTitle());
                        ps.setString(3, book.getAuthor());
                        ps.setInt(4, book.getPublishedYear());

                        ps.setString(
                                5, String.join(";", book.getGenre())
                        );

                        ps.addBatch();
                    }

                    ps.executeBatch();

                    System.out.println(
                            "Books inserted successfully into MySQL."
                    );
                }

            } catch (SQLException e) {
                System.out.println(
                        "Error saving books to MySQL: " + e.getMessage()
                );
            }
        }
    }

    // Main method
    public static void main(String[] args) {

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(System.in))) {

            // Step 1: Input five books
            List<Book> books = Test.inputBooks(br);

            // Step 2: Save books to library_data.txt
            Test.saveBooks(books);

            // Step 3: Read books back from the file
            List<Book> loadedBooks = Test.readBooks();

            // Step 4: Display loaded books
            Test.displayBooks(loadedBooks);

            // Step 5: Search by title or author
            if (!loadedBooks.isEmpty()) {
                Test.searchBooks(br, loadedBooks);
            }

            // Step 6: Optional MySQL bonus
            System.out.print(
                    "\nDo you want to save books to MySQL? (y/n): "
            );

            String answer = br.readLine();

            if (answer != null && answer.equalsIgnoreCase("y")) {
                Test.saveToMySQL(loadedBooks);
            }

        } catch (IOException e) {
            System.out.println(
                    "Input error: " + e.getMessage()
            );
        }
    }
}
