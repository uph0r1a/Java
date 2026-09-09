import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.InputMismatchException;
import java.util.Scanner;

public class Ex14 {
    static int divide(int a, int b) throws ArithmeticException {
        return a / b;
    }

    public static void main(String[] args) throws IOException {
        Scanner sc = new Scanner(System.in);

        System.out.println("--- Wrong input type ---");
        System.out.print("Enter a number: ");
        try {
            int one = sc.nextInt();
            System.out.println("You entered: " + one);
        } catch (InputMismatchException e) {
            System.out.println("Error: that is not a valid integer.");
        } finally {
            sc.nextLine();
        }

        System.out.println("\n--- Divide by zero ---");
        try {
            int result = 5 / 0;
            System.out.println("Result: " + result);
        } catch (ArithmeticException e) {
            System.out.println("Error: cannot divide by zero.");
        }

        System.out.println("\n--- File not found (FileReader) ---");
        try (FileReader fr = new FileReader("files/does_not_exist.txt")) {
            System.out.println("File exists.");
        } catch (FileNotFoundException e) {
            System.out.println("Error: file not found.");
        }

        System.out.println("\n--- Invalid array index ---");
        try {
            int[] arr = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
            int four = arr[arr.length];
            System.out.println("Value: " + four);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Error: array index is out of bounds.");
        }

        System.out.println("\n--- Non-numeric string to number ---");
        try {
            int five = Integer.parseInt("abc");
            System.out.println("Value: " + five);
        } catch (NumberFormatException e) {
            System.out.println("Error: \"abc\" is not a valid number.");
        }

        System.out.println("\n--- File not found (Files.newBufferedReader) ---");
        try (BufferedReader reader = Files.newBufferedReader(Path.of("files/does_not_exist_either.txt"))) {
            System.out.println("File exists.");
        } catch (NoSuchFileException e) {
            System.out.println("Error: no such file - " + e.getFile());
        }

        System.out.println("\n--- Try-catch-finally with a throws declaration ---");
        try {
            int seven = divide(5, 0);
            System.out.println("Value: " + seven);
        } catch (ArithmeticException e) {
            System.out.println("Error: cannot divide by zero.");
        } finally {
            System.out.println("Done");
        }

        System.out.println("\n--- Assert for a positive number ---\nEnter a positive number: ");
        int eight = sc.nextInt();
        try {
            assert eight > 0 : "value must be positive";
            System.out.println("Assertion did not fail (or assertions are disabled). Value: " + eight);
        } catch (AssertionError e) {
            System.out.println("Error: " + e.getMessage());
        }

        sc.close();
    }
}