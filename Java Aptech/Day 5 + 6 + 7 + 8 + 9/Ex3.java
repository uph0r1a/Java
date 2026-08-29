import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

        public Document(String publisherName, int numberOfCopies) {
            this.documentID = UUID.randomUUID().toString();
            this.publisherName = publisherName;
            this.numberOfCopies = numberOfCopies;
        }

        public String getDocumentID() {
            return documentID;
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

    public static class Books extends Document {
        private String authorName;
        private int numberOfPages;

        public Books(String publisherName, int numberOfCopies, String authorName, int numberOfPages) {
            super(publisherName, numberOfCopies);
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

    public static class Magazines extends Document {
        private int issueNumber, issueMonth;

        public Magazines(String publisherName, int numberOfCopies, int issueNumber, int issueMonth) {
            super(publisherName, numberOfCopies);
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

    public static class Newspapers extends Document {
        private LocalDate issueDate;

        public Newspapers(String publisherName, int numberOfCopies, LocalDate issueDate) {
            super(publisherName, numberOfCopies);
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
            System.out.println(
                    "\nNewspaper:\nDocument ID: " + getDocumentID() + "\nPublisher name: " + getPublisherName()
                            + "\nNumber of copies: " + getNumberOfCopies() + "\nIssue date: " + getIssueDate());
        }
    }

    public static class DocumentManager {
        private List<Document> documents;

        public DocumentManager() {
            this.documents = new ArrayList<>();
        }

        public void add() throws IOException {
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
                    documents.add(new Books(publisherName, copies, name, pages));
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

                    documents.add(new Magazines(publisherName, copies, issueNumber, issueMonth));
                }
                case 3 -> {
                    System.out.print("Enter issue date (YYYY-MM-DD): ");
                    LocalDate validDate;
                    while (true) {
                        String userInput = br.readLine();

                        try {
                            validDate = LocalDate.parse(userInput, formatter);
                            break;
                        } catch (DateTimeParseException e) {
                            System.out.print("Invalid date\nRe-enter issue date(YYYY-MM-DD): ");
                        }
                    }
                    documents.add(new Newspapers(publisherName, copies, validDate));
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
                    System.out.println("No document exist");
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

        public void search() {
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
                    case 1 -> document instanceof Books;
                    case 2 -> document instanceof Magazines;
                    case 3 -> document instanceof Newspapers;
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
                    if (choice >= 0 && choice <= 4) {
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
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}
