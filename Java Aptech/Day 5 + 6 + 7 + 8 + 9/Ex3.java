import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

public class Ex3 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    public interface IDisplayable {
        public void display();
    }

    public static abstract class Document implements IDisplayable {
        private String documentID, publisherName;
        private int numberOfCopies;

        public Document(String documentID, String publisherName, int numberOfCopies) {
            this.documentID = documentID;
            this.publisherName = publisherName;
            this.numberOfCopies = numberOfCopies;
        }

        public String getDocumentID() {
            return documentID;
        }

        public void setDocumentID(String documentID) {
            this.documentID = documentID;
        }

        public String getPublisherName() {
            return publisherName;
        }

        public void setPublisherName(String publisherName) {
            this.publisherName = publisherName;
        }

        public int getNumberOfCopies() {
            return numberOfCopies;
        }

        public void setNumberOfCopies(int numberOfCopies) {
            this.numberOfCopies = numberOfCopies;
        }
    }

    public static class Book extends Document {
        private String authorName;
        private int numberOfPages;

        public Book(String documentID, String publisherName, int numberOfCopies, String authorName, int numberOfPages) {
            super(documentID, publisherName, numberOfCopies);
            this.authorName = authorName;
            this.numberOfPages = numberOfPages;
        }

        public String getAuthorName() {
            return authorName;
        }

        public void setAuthorName(String authorName) {
            this.authorName = authorName;
        }

        public int getNumberOfPages() {
            return numberOfPages;
        }

        public void setNumberOfPages(int numberOfPages) {
            this.numberOfPages = numberOfPages;
        }

        @Override
        public void display() {
            System.out.println("\nBook:\nDocument ID: " + getDocumentID() + "\nAuthor name: " + getAuthorName()
                    + "\nPublisher name: " + getPublisherName() + "\nNumber of copies: " + getNumberOfCopies()
                    + "\nNumber of pages: " + getNumberOfPages());
        }
    }

    public static class Magazine extends Document {
        private int issueNumber, issueMonth;

        public Magazine(String documentID, String publisherName, int numberOfCopies, int issueNumber, int issueMonth) {
            super(documentID, publisherName, numberOfCopies);
            this.issueNumber = issueNumber;
            this.issueMonth = issueMonth;
        }

        public int getIssueNumber() {
            return issueNumber;
        }

        public void setIssueNumber(int issueNumber) {
            this.issueNumber = issueNumber;
        }

        public int getIssueMonth() {
            return issueMonth;
        }

        public void setIssueMonth(int issueMonth) {
            this.issueMonth = issueMonth;
        }

        @Override
        public void display() {
            System.out.println("\nMagazine:\nDocument ID: " + getDocumentID() + "\nPublisher name: "
                    + getPublisherName() + "\nNumber of copies: " + getNumberOfCopies() + "\nIssue number: "
                    + getIssueNumber() + "\nIssue month: " + getIssueMonth());
        }
    }

    public static class Newspaper extends Document {
        private LocalDate issueDate;

        public Newspaper(String documentID, String publisherName, int numberOfCopies, LocalDate issueDate) {
            super(documentID, publisherName, numberOfCopies);
            this.issueDate = issueDate;
        }

        public LocalDate getIssueDate() {
            return issueDate;
        }

        public void setIssueDate(LocalDate issueDate) {
            this.issueDate = issueDate;
        }

        @Override
        public void display() {
            System.out
                    .println("\nNewspaper:\nDocument ID: " + getDocumentID() + "\nPublisher name: " + getPublisherName()
                            + "\nNumber of copies: " + getNumberOfCopies() + "\nIssue date: " + getIssueDate());
        }
    }

    public static class DocumentManager {
        private List<Document> documents;

        public DocumentManager() {
            this.documents = new ArrayList<>();
        }

        private boolean idExists(String id) {
            return documents.stream().anyMatch(d -> d.getDocumentID().equals(id));
        }

        public void add() throws IOException {
            System.out.print("Enter document ID: ");
            String documentID;
            while (true) {
                documentID = br.readLine().strip();
                if (!idExists(documentID)) {
                    break;
                }
                System.out.print("Document ID already in use\nRe-enter document ID: ");
            }

            System.out.print("Enter document type 1)Book 2)Magazine 3)Newspaper: ");
            int type;
            while (true) {
                try {
                    type = Integer.parseInt(br.readLine());
                    if (type >= 1 && type <= 3) {
                        break;
                    }
                    System.out.print("Invalid type\nRe-enter document type 1)Book 2)Magazine 3)Newspaper: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter publisher name: ");
            String publisherName = br.readLine();

            System.out.print("Enter number of copies: ");
            int copies;
            while (true) {
                try {
                    copies = Integer.parseInt(br.readLine());
                    if (copies >= 0) {
                        break;
                    }
                    System.out.print("Invalid number of copies\nRe-enter number of copies: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (type) {
                case 1 -> {
                    System.out.print("Enter author name: ");
                    String name = br.readLine();

                    System.out.print("Enter number of pages: ");
                    int pages;
                    while (true) {
                        try {
                            pages = Integer.parseInt(br.readLine());
                            if (pages >= 0) {
                                break;
                            }
                            System.out.print("Invalid number of pages\nRe-enter number of pages: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }
                    documents.add(new Book(documentID, publisherName, copies, name, pages));
                    System.out.println("Book added successfully");
                }
                case 2 -> {
                    System.out.print("Enter issue number: ");
                    int issueNumber;
                    while (true) {
                        try {
                            issueNumber = Integer.parseInt(br.readLine());
                            if (issueNumber >= 0) {
                                break;
                            }
                            System.out.print("Invalid issue number\nRe-enter issue number: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter issue month: ");
                    int issueMonth;
                    while (true) {
                        try {
                            issueMonth = Integer.parseInt(br.readLine());
                            if (issueMonth >= 1 && issueMonth <= 12) {
                                break;
                            }
                            System.out.print("Invalid issue month\nRe-enter issue month: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    documents.add(new Magazine(documentID, publisherName, copies, issueNumber, issueMonth));
                    System.out.println("Magazine added successfully");
                }
                case 3 -> {
                    System.out.print("Enter issue date (uuuu-MM-dd): ");
                    LocalDate validDate;
                    while (true) {
                        String userInput = br.readLine();

                        try {
                            validDate = LocalDate.parse(userInput, formatter);
                            break;
                        } catch (DateTimeParseException e) {
                            System.out.print("Invalid date\nRe-enter issue date (uuuu-MM-dd): ");
                        }
                    }
                    documents.add(new Newspaper(documentID, publisherName, copies, validDate));
                    System.out.println("Newspaper added successfully");
                }
            }
        }

        public void remove() throws IOException {
            if (documents.isEmpty()) {
                System.out.println("No document yet");
            } else {
                System.out.print("Enter document ID: ");
                String id = br.readLine().strip();
                int index = -1;

                for (int i = 0; i < documents.size(); i++) {
                    if (documents.get(i).getDocumentID().equals(id)) {
                        index = i;
                        documents.remove(index);
                        break;
                    }
                }
                if (index == -1) {
                    System.out.println("No document exists with that ID");
                } else {
                    System.out.println("Document removed successfully");
                }
            }
        }

        public void displayAllDocument() {
            if (documents.isEmpty()) {
                System.out.println("No document yet");
            } else {
                for (Document document : documents) {
                    document.display();
                }
            }
        }

        public void search() throws IOException {
            if (documents.isEmpty()) {
                System.out.println("No document yet");
                return;
            }

            System.out.print("Enter document type 1)Book 2)Magazine 3)Newspaper: ");
            int type;
            while (true) {
                try {
                    type = Integer.parseInt(br.readLine());
                    if (type >= 1 && type <= 3) {
                        break;
                    }
                    System.out.print("Invalid type\nRe-enter document type 1)Book 2)Magazine 3)Newspaper: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            boolean found = false;
            for (Document document : documents) {
                boolean matches = switch (type) {
                    case 1 -> document instanceof Book;
                    case 2 -> document instanceof Magazine;
                    case 3 -> document instanceof Newspaper;
                    default -> false;
                };
                if (matches) {
                    document.display();
                    found = true;
                }
            }

            if (!found) {
                System.out.println("No document found for that type.");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        boolean isExit = false;
        DocumentManager manager = new DocumentManager();
        while (!isExit) {
            System.out.print("""
                    ===== LIBRARY MANAGEMENT =====
                    1. Add document
                    2. Remove document
                    3. Display all documents
                    4. Search by type
                    5. Exit
                    Enter your choice:\s""");

            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 1 && choice <= 5) {
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
                case 3 -> manager.displayAllDocument();
                case 4 -> manager.search();
                case 5 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}