import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex4 {
    public static double gallonPaint(double numberOfRoom, double wallSpace) {
        return (wallSpace / 115) * numberOfRoom;
    }

    public static double hoursLabor(double numberOfRoom, double wallSpace) {
        return (wallSpace / 115) * numberOfRoom * 8;
    }

    public static double paintCost(double numberOfRoom, double price, double wallSpace) {
        return gallonPaint(numberOfRoom, wallSpace) * price;
    }

    public static double laborCharges(double numberOfRoom, double wallSpace) {
        return hoursLabor(numberOfRoom, wallSpace) * 18;
    }

    public static double totalCost(double numberOfRoom, double price, double wallSpace) {
        return paintCost(numberOfRoom, price, wallSpace) + laborCharges(numberOfRoom, wallSpace);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter number of room: ");
        double numberOfRoom = Double.parseDouble(br.readLine());

        System.out.print("Enter price: ");
        double price = Double.parseDouble(br.readLine());

        System.out.print("Enter wall space: ");
        double wallSpace = Double.parseDouble(br.readLine());

        System.out.println("The number of gallons of paint required: " + gallonPaint(numberOfRoom, wallSpace)
                + "\nThe hours of labor required: " + hoursLabor(numberOfRoom, wallSpace) + "\nThe cost of the paint: "
                + paintCost(numberOfRoom, price, wallSpace) + "\nThe labor charges: "
                + laborCharges(numberOfRoom, wallSpace) + "\nThe total cost of the paint job: "
                + totalCost(numberOfRoom, price, wallSpace));
    }
}
