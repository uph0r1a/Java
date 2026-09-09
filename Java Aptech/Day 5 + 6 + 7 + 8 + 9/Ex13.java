import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Ex13 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static abstract class Product {
        protected String proID, proName;
        protected int year;
        protected float price;

        public Product() {
        }

        public Product(String proID, String proName, int year, float price) {
            this.proID = proID;
            this.proName = proName;
            this.year = year;
            this.price = price;
        }

        public String getProID() {
            return proID;
        }

        public void setProID(String proID) {
            this.proID = proID;
        }

        public String getProName() {
            return proName;
        }

        public void setProName(String proName) {
            this.proName = proName;
        }

        public int getYear() {
            return year;
        }

        public void setYear(int year) {
            this.year = year;
        }

        public float getPrice() {
            return price;
        }

        public void setPrice(float price) {
            this.price = price;
        }

        public abstract void input(List<String> existingIds) throws IOException;

        public abstract void display();
    }

    public static class Computer extends Product {
        private String speed, producer;

        public Computer() {
        }

        public Computer(String proID, String proName, int year, float price, String speed, String producer) {
            super(proID, proName, year, price);
            this.speed = speed;
            this.producer = producer;
        }

        public String getSpeed() {
            return speed;
        }

        public void setSpeed(String speed) {
            this.speed = speed;
        }

        public String getProducer() {
            return producer;
        }

        public void setProducer(String producer) {
            this.producer = producer;
        }

        @Override
        public void input(List<String> existingIds) throws IOException {
            System.out.print("Enter product ID: ");
            String id;
            while (true) {
                id = br.readLine().strip();
                if (!existingIds.contains(id)) {
                    break;
                }
                System.out.print("ID already exists\nRe-enter product ID: ");
            }

            System.out.print("Enter product name: ");
            String name = br.readLine();

            System.out.print("Enter product year: ");
            int year;
            while (true) {
                try {
                    year = Integer.parseInt(br.readLine());
                    if (year >= 1900 && year <= LocalDate.now().getYear()) {
                        break;
                    }
                    System.out.print("Invalid year\nRe-enter product year: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter product price: ");
            float price;
            while (true) {
                try {
                    price = Float.parseFloat(br.readLine());
                    if (price >= 0) {
                        break;
                    }
                    System.out.print("Invalid product price\nRe-enter product price: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter product speed: ");
            String speed = br.readLine().strip();

            System.out.print("Enter product producer: ");
            String producer = br.readLine();

            setProID(id);
            setProName(name);
            setYear(year);
            setPrice(price);
            setSpeed(speed);
            setProducer(producer);
        }

        @Override
        public void display() {
            System.out.println("\nProduct ID: " + getProID() + "\nProduct name: " + getProName() + "\nProduct year: "
                    + getYear() + "\nProduct price: " + getPrice() + "\nProduct speed: " + getSpeed()
                    + "\nProduct producer: " + getProducer());
        }
    }

    public static class Book extends Product {
        private String type, publisher;

        public Book() {
        }

        public Book(String proID, String proName, int year, float price, String type, String publisher) {
            super(proID, proName, year, price);
            this.type = type;
            this.publisher = publisher;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPublisher() {
            return publisher;
        }

        public void setPublisher(String publisher) {
            this.publisher = publisher;
        }

        @Override
        public void input(List<String> existingIds) throws IOException {
            System.out.print("Enter product ID: ");
            String id;
            while (true) {
                id = br.readLine().strip();
                if (!existingIds.contains(id)) {
                    break;
                }
                System.out.print("ID already exists\nRe-enter product ID: ");
            }

            System.out.print("Enter product name: ");
            String name = br.readLine();

            System.out.print("Enter product year: ");
            int year;
            while (true) {
                try {
                    year = Integer.parseInt(br.readLine());
                    if (year >= 1900 && year <= LocalDate.now().getYear()) {
                        break;
                    }
                    System.out.print("Invalid year\nRe-enter product year: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter product price: ");
            float price;
            while (true) {
                try {
                    price = Float.parseFloat(br.readLine());
                    if (price >= 0) {
                        break;
                    }
                    System.out.print("Invalid product price\nRe-enter product price: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter product type: ");
            String type = br.readLine().strip();

            System.out.print("Enter product publisher: ");
            String publisher = br.readLine();

            setProID(id);
            setProName(name);
            setYear(year);
            setPrice(price);
            setType(type);
            setPublisher(publisher);
        }

        @Override
        public void display() {
            System.out.println("\nProduct ID: " + getProID() + "\nProduct name: " + getProName() + "\nProduct year: "
                    + getYear() + "\nProduct price: " + getPrice() + "\nProduct type: " + getType()
                    + "\nProduct publisher: " + getPublisher());
        }
    }

    public static void main(String[] args) throws IOException {
        List<Computer> computers = new ArrayList<>();
        List<Book> books = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    ===== COMPUTER & BOOK PRODUCT CATALOG =====
                    1. Input information for n Computers.
                    2. Input information for n Books.
                    3. Display information of n Computers by sorting the price descending.
                    4. Display information of n Books by sorting the publisher ascending.
                    5. Exit.
                    Your choice:\s""");
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
                case 1 -> {
                    System.out.print("Enter number of computers: ");
                    int number;
                    while (true) {
                        try {
                            number = Integer.parseInt(br.readLine());
                            if (number >= 0) {
                                break;
                            }
                            System.out.print("Invalid number of computers\nRe-enter number of computers: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }
                    for (int i = 0; i < number; i++) {
                        List<String> existingIds = new ArrayList<>();
                        computers.forEach(c -> existingIds.add(c.getProID()));
                        books.forEach(b -> existingIds.add(b.getProID()));

                        Computer computer = new Computer();
                        computer.input(existingIds);
                        computers.add(computer);
                    }
                }
                case 2 -> {
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
                        List<String> existingIds = new ArrayList<>();
                        computers.forEach(c -> existingIds.add(c.getProID()));
                        books.forEach(b -> existingIds.add(b.getProID()));

                        Book book = new Book();
                        book.input(existingIds);
                        books.add(book);
                    }
                }
                case 3 -> {
                    if (computers.isEmpty()) {
                        System.out.println("No computer yet");
                    } else {
                        computers.stream().sorted(Comparator.comparingDouble(Computer::getPrice).reversed())
                                .forEach(Computer::display);
                    }
                }
                case 4 -> {
                    if (books.isEmpty()) {
                        System.out.println("No book yet");
                    } else {
                        books.stream().sorted(Comparator.comparing(Book::getPublisher)).forEach(Book::display);
                    }
                }
                case 5 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}