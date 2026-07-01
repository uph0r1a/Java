import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ex15 {
    static class RoulettePocket {
        private int pocketNumber;

        public RoulettePocket(int pocketNumber) {
            this.pocketNumber = pocketNumber;
        }

        public String getPocketColor() {
            if (pocketNumber >= 29) {
                return (pocketNumber % 2 == 0) ? "Red" : "Black";
            } else if (pocketNumber >= 19) {
                return (pocketNumber % 2 != 0) ? "Red" : "Black";
            } else if (pocketNumber >= 11) {
                return (pocketNumber % 2 == 0) ? "Red" : "Black";
            } else if (pocketNumber >= 1) {
                return (pocketNumber % 2 != 0) ? "Red" : "Black";
            } else {
                return "Green";
            }
        }
    }

    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter a pocket number: ");
        int num;
        while (true) {
            try {
                num = Integer.parseInt(br.readLine());
                if (num >= 0 && num <= 36) {
                    break;
                }
                System.out.print("Invalid number\nRe-enter pocket number: ");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        RoulettePocket roulettePocket = new RoulettePocket(num);
        System.out.println("Color: " + roulettePocket.getPocketColor());
    }
}
