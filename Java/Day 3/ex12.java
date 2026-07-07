import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex12 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter medium: ");
        String medium = br.readLine().toLowerCase().strip();

        System.out.print("Enter distance: ");
        double distance = Double.parseDouble(br.readLine());

        double time;
        if (medium.equals("air")) {
            time = distance / 1100;
        } else if (medium.equals("water")) {
            time = distance / 4900;
        } else {
            time = distance / 16400;
        }

        System.out.println("Amount of time: " + time);
    }
}
