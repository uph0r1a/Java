import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Ex19 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static class Book implements Serializable {
        private String isbn, bookName, author, publisher;
        private float price;

        public Book() {
        }

        public Book(String isbn, String bookName, String author, String publisher, float price) {
            this.isbn = isbn;
            this.bookName = bookName;
            this.author = author;
            this.publisher = publisher;
            this.price = price;
        }

        public String getIsbn() {
            return isbn;
        }

        public void setIsbn(String isbn) {
            this.isbn = isbn;
        }

        public String getBookName() {
            return bookName;
        }

        public void setBookName(String bookName) {
            this.bookName = bookName;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getPublisher() {
            return publisher;
        }

        public void setPublisher(String publisher) {
            this.publisher = publisher;
        }

        public float getPrice() {
            return price;
        }

        public void setPrice(float price) {
            this.price = price;
        }

        @Override
        public String toString() {
            return isbn + "|" + bookName + "|" + author + "|" + publisher + "|" + price;
        }
    }

    public static class BookManager {

        public void write() throws IOException {
            System.out.print("Enter number of book: ");
            int n;
            while (true) {
                try {
                    n = Integer.parseInt(br.readLine());
                    if (n > 0) {
                        break;
                    }
                    System.out.print("Invalid number of book\nRe-enter number of book: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            List<Book> books = new ArrayList<>();

            for (int i = 0; i < n; i++) {
                System.out.print("Enter isbn: ");
                String isbn = br.readLine().trim();

                System.out.print("Enter book name: ");
                String name = br.readLine().trim();

                System.out.print("Enter author: ");
                String author = br.readLine().trim();

                System.out.print("Enter publisher: ");
                String publisher = br.readLine().trim();

                System.out.print("Enter price: ");
                float price;
                while (true) {
                    try {
                        price = Float.parseFloat(br.readLine());
                        if (price >= 0) {
                            break;
                        }
                        System.out.print("Invalid price\nRe-enter price: ");
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                }

                books.add(new Book(isbn, name, author, publisher, price));
            }

            File dir = new File("files");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter("files/book.txt")))) {
                for (Book book : books) {
                    writer.println(book);
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    public static class BookRead {
        private List<Book> books;

        public BookRead() {
            books = new ArrayList<>();
        }

        public void read() {
            try (BufferedReader reader = new BufferedReader(new FileReader("files/book.txt"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] value = line.split("\\|");
                    books.add(new Book(value[0], value[1], value[2], value[3], Float.parseFloat(value[4])));
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }

            for (Book book : books) {
                System.out.println(book);
            }
        }

        public void findBook() throws IOException {
            System.out.print("Enter book name: ");
            String keyword = br.readLine().trim().toLowerCase();

            boolean found = false;

            for (Book book : books) {
                if (book.getBookName().toLowerCase().contains(keyword)) {
                    found = true;
                    System.out.println(book);
                }
            }

            if (!found) {
                System.out.println("None match");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        BookManager bookManager = new BookManager();
        BookRead bookRead = new BookRead();

        bookManager.write();
        bookRead.read();
        bookRead.findBook();
    }
}