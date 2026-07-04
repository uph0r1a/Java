import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex5 {
    static class Password {
        private String pass;

        public Password(String pass) {
            this.pass = pass;
        }

        public boolean isValidPass() {
            return pass.length() >= 6 && pass.matches(".*[A-Z].*") && pass.matches(".*[a-z].*")
                    && pass.matches(".*[0-9].*");
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter password: ");
        String pass = br.readLine();

        Password password = new Password(pass);
        System.out.println(password.isValidPass() ? "Valid" : "Not valid");
    }
}
