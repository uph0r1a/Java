public class ex3 {

    public static void printReverse(String str) {
        if (str.isEmpty()) {
            return;
        }
        printReverse(str.substring(1));
        System.out.print(str.charAt(0));
    }

    public static void main(String[] args) {
        String message = "Hello, World!";

        System.out.print("Original: " + message + "\nReversed: ");
        printReverse(message);
        System.out.println();
    }
}