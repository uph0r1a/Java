import java.util.Scanner;

public class ex4 {
    static class Word {
        private String str;

        public Word(String str) {
            this.str = str;
        }

        public int getVowel() {
            int count = 0;

            for (char c : str.toLowerCase().toCharArray()) {
                if ("aeiou".indexOf(c) != -1) {
                    count++;
                }
            }
            return count;
        }

        public int getConsonants() {
            int count = 0;

            for (char c : str.toLowerCase().toCharArray()) {
                if (Character.isLetter(c) && "aeiou".indexOf(c) == -1) {
                    count++;
                }
            }
            return count;
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Enter a string: ");
        String str = sc.nextLine();

        Word word = new Word(str);

        char choice;

        do {
            System.out.println(
                    "\nMenu\na. Count the number of vowels in the string\nb. Count the number of consonants in the string\nc. Count both the vowels and consonants in the string\nd. Enter another string\ne. Exit the program\nEnter your choice: ");

            choice = sc.next().toLowerCase().charAt(0);
            sc.nextLine();

            switch (choice) {
                case 'a':
                    System.out.println("Number of vowels: " + word.getVowel());
                    break;

                case 'b':
                    System.out.println("Number of consonants: " + word.getConsonants());
                    break;

                case 'c':
                    System.out.println(
                            "Number of vowels: " + word.getVowel() + "\nNumber of consonants: " + word.getConsonants());
                    break;

                case 'd':
                    System.out.print("Enter another string: ");
                    str = sc.nextLine();
                    word = new Word(str);
                    break;

                case 'e':
                    System.out.println("Program exited.");
                    break;

                default:
                    System.out.println("Invalid choice! Please enter a, b, c, d, or e.");
            }

        } while (choice != 'e');

        sc.close();
    }
}