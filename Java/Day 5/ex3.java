import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex3 {
    static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static double getLength() throws NumberFormatException, IOException {
        System.out.print("Enter rectangle length: ");
        return Double.parseDouble(br.readLine());
    }

    public static double getWidth() throws NumberFormatException, IOException {
        System.out.print("Enter rectangle width: ");
        return Double.parseDouble(br.readLine());
    }

    public static double getArea(double length, double width) {
        return length * width;
    }

    public static void displayData(double length, double width, double area) {
        System.out.println("Length: " + length + "\nWidth: " + width + "\nArea: " + area);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        double length = getLength(), width = getWidth(), area = getArea(length, width);
        displayData(length, width, area);
    }
}
