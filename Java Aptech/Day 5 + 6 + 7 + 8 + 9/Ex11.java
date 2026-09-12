import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Ex11 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static interface IBook {
        void addCopies(int quantity);

        void removeCopies(int quantity);

        void displayInfo();
    }

    public static class Book implements IBook {
        private String id, title, author;
        private double price;
        private int quantity;

        public Book(String id, String title, String author, double price, int quantity) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.price = price;
            this.quantity = quantity;
        }

        public String getId() {
            return id;
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

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        @Override
        public void addCopies(int quantity) {
            this.quantity += quantity;
        }

        @Override
        public void removeCopies(int quantity) {
            if (this.quantity < quantity) {
                System.out.println("Cant remove more book than in stock");
                return;
            }
            this.quantity -= quantity;
        }

        @Override
        public void displayInfo() {
            System.out.println("\nID: " + id + "\nTitle: " + title + "\nAuthor: " + author + "\nPrice: " + price
                    + "\nQuantity: " + quantity);
        }
    }

    public static class Textbook extends Book {
        private String subject, academicLevel;

        public Textbook(String id, String title, String author, double price, int quantity, String subject,
                String academicLevel) {
            super(id, title, author, price, quantity);
            this.subject = subject;
            this.academicLevel = academicLevel;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getAcademicLevel() {
            return academicLevel;
        }

        public void updateAcademicLevel(String update) {
            academicLevel = update;
        }

        @Override
        public void displayInfo() {
            super.displayInfo();
            System.out.println("\nSubject: " + subject + "\nAcademic level: " + academicLevel);
        }
    }

    public static class Novel extends Book {
        private String genre;
        private boolean isBestseller;

        public Novel(String id, String title, String author, double price, int quantity, String genre,
                boolean isBestseller) {
            super(id, title, author, price, quantity);
            this.genre = genre;
            this.isBestseller = isBestseller;
        }

        public String getGenre() {
            return genre;
        }

        public void setGenre(String genre) {
            this.genre = genre;
        }

        public boolean isBestseller() {
            return isBestseller;
        }

        public void setBestseller(boolean isBestseller) {
            this.isBestseller = isBestseller;
        }

        public void markAsBestseller() {
            isBestseller = true;
        }

        @Override
        public void displayInfo() {
            super.displayInfo();
            System.out.println("\nGenre: " + genre + "\nIs best seller: " + (isBestseller ? "Yes" : "No"));
        }
    }

    public static class ReferenceBook extends Book {
        private String fieldOfStudy, edition;

        public ReferenceBook(String id, String title, String author, double price, int quantity, String fieldOfStudy,
                String edition) {
            super(id, title, author, price, quantity);
            this.fieldOfStudy = fieldOfStudy;
            this.edition = edition;
        }

        public String getFieldOfStudy() {
            return fieldOfStudy;
        }

        public void setFieldOfStudy(String fieldOfStudy) {
            this.fieldOfStudy = fieldOfStudy;
        }

        public String getEdition() {
            return edition;
        }

        public void setEdition(String edition) {
            this.edition = edition;
        }

        public void updateEdition(String updateEdition) {
            this.edition = updateEdition;
        }

        @Override
        public void displayInfo() {
            super.displayInfo();
            System.out.println("\nField of study: " + fieldOfStudy + "\nEdition: " + edition);
        }
    }

    public static class LibraryManager {
        private Map<String, Book> library;

        public LibraryManager() {
            library = new HashMap<>();
        }

        public void add() throws IOException {
            System.out.print("Enter book id: ");
            String id;
            while (true) {
                id = br.readLine().strip();
                if (!library.containsKey(id)) {
                    break;
                }
                System.out.print("ID already exist\nRe-enter books id: ");
            }

            System.out.print("Enter book title: ");
            String title = br.readLine().strip();

            System.out.print("Enter book author: ");
            String author = br.readLine().strip();

            System.out.print("Enter book price: ");
            double price;
            while (true) {
                try {
                    price = Double.parseDouble(br.readLine());
                    if (price >= 0) {
                        break;
                    }
                    System.out.print("Invalid price\nRe-enter book price: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter book quantity: ");
            int quantity;
            while (true) {
                try {
                    quantity = Integer.parseInt(br.readLine());
                    if (quantity >= 0) {
                        break;
                    }
                    System.out.print("Invalid\nRe-enter book quantity: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter book type (Textbook/Novel/Reference book): ");
            String type;
            while (true) {
                type = br.readLine().strip();
                if (type.equalsIgnoreCase("textbook") || type.equalsIgnoreCase("novel")
                        || type.equalsIgnoreCase("reference book")) {
                    break;
                }
                System.out.print("Invalid type\nRe-enter book type (Textbook/Novel/Reference book): ");
            }

            switch (type.toLowerCase()) {
                case "textbook" -> {
                    System.out.print("Enter book subject: ");
                    String subject = br.readLine().strip();

                    System.out.print("Enter academic level: ");
                    String level = br.readLine().strip();

                    library.put(id, new Textbook(id, title, author, price, quantity, subject, level));
                    System.out.println("Textbook added successfully");
                }
                case "novel" -> {
                    System.out.print("Enter book genre: ");
                    String genre = br.readLine().strip();

                    int bestSeller;
                    while (true) {
                        System.out.print("Is a best seller 1) Yes 2) No: ");
                        try {
                            bestSeller = Integer.parseInt(br.readLine());
                            if (bestSeller == 1 || bestSeller == 2) {
                                break;
                            }
                            System.out.println("Invalid choice");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    library.put(id, new Novel(id, title, author, price, quantity, genre, bestSeller == 1));
                    System.out.println("Novel added successfully");
                }
                case "reference book" -> {
                    System.out.print("Enter book field of study: ");
                    String field = br.readLine().strip();

                    System.out.print("Enter book edition: ");
                    String edition = br.readLine().strip();

                    library.put(id, new ReferenceBook(id, title, author, price, quantity, field, edition));
                    System.out.println("Reference book added successfully");
                }
            }
        }

        public void remove() throws IOException {
            if (library.isEmpty()) {
                System.out.println("No books yet");
                return;
            }

            System.out.print("Enter book id: ");
            String id = br.readLine().strip();

            if (library.remove(id) != null) {
                System.out.println("Book removed successfully");
            } else {
                System.out.println("Book not found");
            }
        }

        public void findByTitle() throws IOException {
            if (library.isEmpty()) {
                System.out.println("No books yet");
                return;
            }

            System.out.print("Enter book title: ");
            String title = br.readLine().strip();

            List<Book> results = library.values().stream()
                    .filter(b -> b.getTitle().equalsIgnoreCase(title)).toList();

            if (results.isEmpty()) {
                System.out.println("No book found with that title");
            } else {
                results.forEach(Book::displayInfo);
            }
        }

        public void findByCategory() throws IOException {
            if (library.isEmpty()) {
                System.out.println("No books yet");
                return;
            }

            System.out.print("Enter book type (Textbook/Novel/Reference book): ");
            String type;
            while (true) {
                type = br.readLine().strip();
                if (type.equalsIgnoreCase("textbook") || type.equalsIgnoreCase("novel")
                        || type.equalsIgnoreCase("reference book")) {
                    break;
                }
                System.out.print("Invalid type\nRe-enter book type (Textbook/Novel/Reference book): ");
            }
            final String t = type;

            List<Book> results = library.values().stream().filter(b -> switch (t.toLowerCase()) {
                case "textbook" -> b instanceof Textbook;
                case "novel" -> b instanceof Novel;
                case "reference book" -> b instanceof ReferenceBook;
                default -> false;
            }).toList();

            if (results.isEmpty()) {
                System.out.println("No book found in that category");
            } else {
                results.forEach(Book::displayInfo);
            }
        }

        public void updateTextbookAcademicLevel() throws IOException {
            if (library.isEmpty()) {
                System.out.println("No books yet");
                return;
            }

            System.out.print("Enter book id: ");
            String id = br.readLine().strip();
            Book book = library.get(id);

            if (book == null) {
                System.out.println("Book not found");
            } else if (!(book instanceof Textbook textbook)) {
                System.out.println("This book is not a Textbook");
            } else {
                System.out.print("Enter academic level: ");
                String level = br.readLine().strip();
                textbook.updateAcademicLevel(level);
                System.out.println("Academic level updated");
            }
        }

        public void markNovelAsBestseller() throws IOException {
            if (library.isEmpty()) {
                System.out.println("No books yet");
                return;
            }

            System.out.print("Enter book id: ");
            String id = br.readLine().strip();
            Book book = library.get(id);

            if (book == null) {
                System.out.println("Book not found");
            } else if (!(book instanceof Novel novel)) {
                System.out.println("This book is not a Novel");
            } else {
                novel.markAsBestseller();
                System.out.println("Marked as bestseller");
            }
        }

        public void updateReferenceBookEdition() throws IOException {
            if (library.isEmpty()) {
                System.out.println("No books yet");
                return;
            }

            System.out.print("Enter book id: ");
            String id = br.readLine().strip();
            Book book = library.get(id);

            if (book == null) {
                System.out.println("Book not found");
            } else if (!(book instanceof ReferenceBook referenceBook)) {
                System.out.println("This book is not a Reference book");
            } else {
                System.out.print("Enter book edition: ");
                String edition = br.readLine().strip();
                referenceBook.updateEdition(edition);
                System.out.println("Edition updated");
            }
        }

        public void display() {
            if (library.isEmpty()) {
                System.out.println("No books yet");
            } else {
                library.values().forEach(Book::displayInfo);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        LibraryManager manager = new LibraryManager();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    ===== BOOK LIBRARY MANAGEMENT =====
                    1) Add book
                    2) Remove book
                    3) Find book by title
                    4) Find book by category
                    5) Update textbook academic level
                    6) Mark novel as bestseller
                    7) Update reference book edition
                    8) Display all books
                    0) Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 8) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (choice) {
                case 1 -> manager.add();
                case 2 -> manager.remove();
                case 3 -> manager.findByTitle();
                case 4 -> manager.findByCategory();
                case 5 -> manager.updateTextbookAcademicLevel();
                case 6 -> manager.markNovelAsBestseller();
                case 7 -> manager.updateReferenceBookEdition();
                case 8 -> manager.display();
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}