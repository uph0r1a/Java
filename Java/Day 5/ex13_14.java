import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ex13_14 {
    static boolean isPrime(int n, int i) {
        if (n <= 2)
            return n == 2;
        if (n % i == 0)
            return false;
        if (i * i > n)
            return true;

        return isPrime(n, i + 1);
    }

    public static void main(String[] args) {
        try {
            StringBuilder sb = new StringBuilder();

            for (int i = 1; i <= 100; i++) {
                if (isPrime(i, 2)) {
                    sb.append(i).append("\n");
                }
            }

            Files.writeString(Path.of("files/prime.txt"), sb.toString(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
