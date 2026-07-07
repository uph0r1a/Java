import java.util.Random;

public class ex15 {
    public static boolean isEven(int n) {
        return (n % 2) == 0;
    }

    public static void main(String[] args) {
        Random rand = new Random();

        int even = 0;

        for (int i = 0; i < 100; i++) {
            if (isEven(rand.nextInt())) {
                even++;
            }
        }

        System.out.println("Even: " + even + "\nOdd: " + (100 - even));
    }
}
