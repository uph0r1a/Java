import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex12 {
    public static double kineticEnergy(double mass, double velocity) {
        return 0.5 * mass * Math.pow(velocity, 2);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter mass: ");
        double m = Double.parseDouble(br.readLine());

        System.out.print("Enter velocity: ");
        double v = Double.parseDouble(br.readLine());

        System.out.println("Kinetic energy: " + kineticEnergy(m, v));
    }
}
