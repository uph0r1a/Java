import java.io.*;
import java.util.*;

public class Ex3 {
    static class Book implements Serializable {
        private static final long serialVersionUID = 1L;
        private String bookID, title, author;

        public Book() {
        }

        public Book(String bookID, String title, String author) {
            this.bookID = bookID;
            this.title = title;
            this.author = author;
        }

        public String getBookID() {
            return bookID;
        }

        public void setBookID(String bookID) {
            this.bookID = bookID;
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

        @Override
        public String toString() {
            return "ID: " + bookID + ", Title: " + title + ", Author: " + author;
        }
    }

    static class LibraryManager {
        private static final String FILE_NAME = "library_books.ser", ID_PATTERN = "^[FNS][MH][PD]\\d{4}$";
        private ArrayList<Book> books;

        public LibraryManager() {
            books = new ArrayList<>();
        }

        public String readBookID(BufferedReader br) throws IOException {
            while (true) {
                System.out.print("Enter Book ID: ");
                String id = br.readLine();

                if (id == null) {
                    throw new EOFException("Input stream closed.");
                }

                id = id.trim();

                if (id.matches(ID_PATTERN)) {
                    return id;
                }

                System.out.println(
                        "Invalid Book ID. Please try again.\nFormat: [FNS][MH][PD] followed by 4 digits.\nExample: FMP0042");
            }
        }

        public String readText(BufferedReader br, String message) throws IOException {
            while (true) {
                System.out.print(message);
                String input = br.readLine();

                if (input == null) {
                    throw new EOFException("Input stream closed.");
                }

                input = input.trim();

                if (!input.isEmpty()) {
                    return input;
                }

                System.out.println("Input cannot be empty.");
            }
        }

        public void inputBooks(BufferedReader br) throws IOException {
            System.out.println("\n========== ENTER 5 BOOKS ==========");

            for (int i = 0; i < 5; i++) {
                System.out.println("\nBook " + (i + 1) + ":");

                String id = readBookID(br), title = readText(br, "Enter Title: "),
                        author = readText(br, "Enter Author: ");
                Book book = new Book(id, title, author);
                books.add(book);

                System.out.println("Book added successfully.");
            }
        }

        public void serializeBooks() {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_NAME))) {
                oos.writeObject(books);

                System.out.println("\nBooks serialized successfully.\nFile: " + FILE_NAME);
            } catch (IOException e) {
                System.out.println("Serialization error: " + e.getMessage());
            }
        }

        @SuppressWarnings("unchecked")
        public ArrayList<Book> deserializeBooks() {
            ArrayList<Book> loadedBooks = new ArrayList<>();

            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILE_NAME))) {
                loadedBooks = (ArrayList<Book>) ois.readObject();
                System.out.println("\nBooks deserialized successfully.");
            } catch (FileNotFoundException e) {
                System.out.println("Error: Serialization file not found.");
            } catch (ClassNotFoundException e) {
                System.out.println("Error: Book class could not be found.");
            } catch (IOException e) {
                System.out.println("Deserialization error: " + e.getMessage());
            }

            return loadedBooks;
        }

        public void displayBooks(List<Book> books) {
            if (books == null || books.isEmpty()) {
                System.out.println("No books available.");
                return;
            }

            System.out.println("\n========== LIBRARY BOOK LIST ==========");

            for (Book book : books) {
                System.out.println(book);
            }
        }

        public int countBooksByAuthor(List<Book> books, String author) {
            if (books == null || author == null) {
                return 0;
            }

            int count = 0;

            for (Book book : books) {
                if (book.getAuthor().equalsIgnoreCase(author)) {
                    count++;
                }
            }

            return count;
        }

        public ArrayList<Book> getBooks() {
            return books;
        }
    }

    public static void main(String[] args) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            LibraryManager manager = new LibraryManager();
            manager.inputBooks(br);
            manager.serializeBooks();

            ArrayList<Book> loadedBooks = manager.deserializeBooks();
            manager.displayBooks(loadedBooks);

            if (!loadedBooks.isEmpty()) {
                System.out.println("\n========== COUNT BOOKS BY AUTHOR ==========");

                String author = manager.readText(br, "Enter author name: ");
                int count = manager.countBooksByAuthor(loadedBooks, author);

                System.out.println("Number of books by " + author + ": " + count);
            }
        } catch (EOFException e) {
            System.out.println("\nInput ended unexpectedly.");
        } catch (IOException e) {
            System.out.println("Input error: " + e.getMessage());
        }
    }
}
