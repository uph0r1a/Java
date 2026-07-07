import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ex3_4 {
    static final List<Integer> account = new ArrayList<>();

    static class ChargeAccount {
        public static boolean isValidAccount(int acc) {
            return account.contains(acc);
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        try {
            for (String line : Files.readAllLines(Path.of("files/account.txt"))) {
                account.add(Integer.parseInt(line.trim()));
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.print("Enter a account: ");
        int a = Integer.parseInt(br.readLine());

        if (ChargeAccount.isValidAccount(a)) {
            System.out.println("Valid");
        } else {
            System.out.println("Invalid");
        }
    }
}
