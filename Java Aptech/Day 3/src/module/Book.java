package module;

public class Book {
    private String isbn;
    private String title;
    private String author;
    private int yearPublish;
    private double price;

    public Book() {
    }

    public Book(String isbn, String title, String author, int yearPublish, double price) {
        this.isbn = isbn;
        this.title = title;
        this.author = title;
        this.author = author;
        this.yearPublish = yearPublish;
        this.price = price;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public int getYearPublish() {
        return yearPublish;
    }

    public double getPrice() {
        return price;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setYearPublish(int yearPublish) {
        this.yearPublish = yearPublish;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "ISBN: " + isbn
                + "\nTitle: " + title
                + "\nAuthor: " + author
                + "\nYear Publish: " + yearPublish
                + "\nPrice: $" + String.format("%.2f", price);
    }
}