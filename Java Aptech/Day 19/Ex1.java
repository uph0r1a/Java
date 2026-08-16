import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Ex1 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static class Book {
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
            return "\nISBN: " + isbn + "\nBook name: " + bookName + "\nAuthor: " + author + "\nPublisher: " + publisher
                    + "\nPrice: " + price + "\n";
        }
    }

    public static class BookManager {
        private List<Book> books;

        public BookManager() {
            books = new ArrayList<>();
        }

        public void input() throws IOException {
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

            for (int i = 0; i < n; i++) {
                System.out.print("Enter ISBN: ");
                String isbn = br.readLine();

                System.out.print("Enter book name: ");
                String name = br.readLine();

                System.out.print("Enter book author: ");
                String author = br.readLine();

                System.out.print("Enter book publisher: ");
                String publisher = br.readLine();

                System.out.print("Enter book price: ");
                float price;
                while (true) {
                    try {
                        price = Float.parseFloat(br.readLine());
                        if (price >= 0) {
                            break;
                        }
                        System.out.print("Invalid price\nRe-enter book price: ");
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                }

                books.add(new Book(isbn, name, author, publisher, price));
            }
        }

        public void display() {
            if (books.isEmpty()) {
                System.out.println("No book yet");
            } else {
                for (int i = 0; i < books.size(); i++) {
                    System.out.println("Book " + (i + 1) + ": " + books.get(i).toString());
                }
            }
        }

        public void sort() {
            if (books.isEmpty()) {
                System.out.println("No book yet");
            } else {
                boolean swapped;

                for (int i = 0; i < books.size() - 1; i++) {
                    swapped = false;
                    for (int j = 0; j < books.size() - i - 1; j++) {
                        if (books.get(j).getPrice() < books.get(j + 1).getPrice()) {
                            Book temp = books.get(j);
                            books.set(j, books.get(j + 1));
                            books.set(j + 1, temp);
                            swapped = true;
                        }
                    }

                    if (!swapped) {
                        break;
                    }
                }

                display();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        BookManager bookManager = new BookManager();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    1) Input book
                    2) Display book
                    3) Sorted book
                    0) Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 3) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            switch (choice) {
                case 1 -> bookManager.input();
                case 2 -> bookManager.display();
                case 3 -> bookManager.sort();
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}
