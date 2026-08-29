import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Ex5 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static interface IDocument {
        public void input() throws IOException;

        public void display();
    }

    public static class Book implements IDocument {
        private int id;
        private String bookTitle, authorName;
        private float price;

        public Book() {
        }

        public Book(int id, String bookTitle, String authorName, float price) {
            this.id = id;
            this.bookTitle = bookTitle;
            this.authorName = authorName;
            this.price = price;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getBookTitle() {
            return bookTitle;
        }

        public void setBookTitle(String bookTitle) {
            this.bookTitle = bookTitle;
        }

        public String getAuthorName() {
            return authorName;
        }

        public void setAuthorName(String authorName) {
            this.authorName = authorName;
        }

        public float getPrice() {
            return price;
        }

        public void setPrice(float price) {
            this.price = price;
        }

        @Override
        public void input() throws IOException {
            System.out.print("Enter book id: ");
            int id;
            while (true) {
                try {
                    id = Integer.parseInt(br.readLine());
                    if (id > 0) {
                        break;
                    }
                    System.out.print("Invalid id\nRe-enter book id: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter book title: ");
            String title = br.readLine().strip();

            System.out.print("Enter author name: ");
            String author = br.readLine().strip();

            System.out.print("Enter book price: ");
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

            setId(id);
            setBookTitle(title);
            setAuthorName(author);
            setPrice(price);
        }

        @Override
        public void display() {
            System.out.println("\nID: " + getId() + "\nBook title: " + getBookTitle() + "\nAuthor name: "
                    + getAuthorName() + "\nPrice: " + getPrice());
        }
    }

    public static class DocumentManager {
        private List<Book> books;

        public DocumentManager() {
            books = new ArrayList<>();
        }

        public void addDocument() throws IOException {
            System.out.print("Enter number of books: ");
            int number;
            while (true) {
                try {
                    number = Integer.parseInt(br.readLine());
                    if (number >= 0) {
                        break;
                    }
                    System.out.print("Invalid number of books\nRe-enter number of books: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            for (int i = 0; i < number; i++) {
                Book b = new Book();
                b.input();
                books.add(b);
            }
        }

        public void displayAllDocuments() {
            if (books.isEmpty()) {
                System.out.println("No book yet");
            } else {
                for (Book book : books) {
                    book.display();
                }
            }
        }

        public void searchByAuthor(String authorName) {
            if (books.isEmpty()) {
                System.out.println("No book yet");
                return;
            }

            boolean isFound = false;
            for (Book book : books) {
                if (book.getAuthorName().equalsIgnoreCase(authorName)) {
                    book.display();
                    isFound = true;
                }
            }
            if (!isFound) {
                System.out.println("Not found");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        DocumentManager manager = new DocumentManager();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    1. Add new book
                    2. Display all books
                    3. Search books by author
                    4. Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 1 && choice <= 4) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            switch (choice) {
                case 1 -> manager.addDocument();
                case 2 -> manager.displayAllDocuments();
                case 3 -> {
                    System.out.print("Enter author name: ");
                    String author = br.readLine().strip();
                    manager.searchByAuthor(author);
                }
                case 4 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}