import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ex3 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter the speed: ");
        double speed;
        while (true) {
            speed = Double.parseDouble(br.readLine());
            if (speed >= 0) {
                break;
            }
            System.out.print("Speed cannot be negative\nRe-enter the speed: ");
        }

        System.out.print("Enter the number of hour traveled: ");
        int hour;
        while (true) {
            hour = Integer.parseInt(br.readLine());
            if (hour >= 1) {
                break;
            }
            System.out.print("Time cant be less than 1\nRe-enter time: ");
        }

        try {
            StringBuilder sb = new StringBuilder();

            sb.append(String.format("%-5s %s%n", "Hour", "Distance Traveled"));
            sb.append("-".repeat(23)).append("\n");

            for (int i = 1; i <= hour; i++) {
                sb.append(String.format("%-10d %.2f%n", i, speed * i));
            }

            Files.writeString(Path.of("files", "distance.txt"), sb.toString(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.out.print("Error: ");
            e.printStackTrace();
        }
    }
}
