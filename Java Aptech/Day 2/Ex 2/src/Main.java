import model.Book;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter the number of books: ");
        int numberOfBook = scanner.nextInt();
        scanner.nextLine();

        Book[] book = new Book[numberOfBook];

        for (int i = 0; i < numberOfBook; i++) {
            book[i] = new Book();

            System.out.print("Enter book " + (i + 1) + " title: ");
            book[i].title = scanner.nextLine();

            System.out.print("Enter book " + (i + 1) + " author: ");
            book[i].author = scanner.nextLine();

            System.out.print("Enter book " + (i + 1) + " price: ");
            book[i].price = scanner.nextFloat();
            scanner.nextLine();

            System.out.println("Book " + (i + 1) + "\nTitle: " + book[i].title + "\nAuthor: " + book[i].author
                    + "\nPrice: " + book[i].price);
        }
    }
}