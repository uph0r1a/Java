import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex7 {
    static class Circle {
        private double radius;
        private final double PI = 3.14159;

        public Circle(double radius) {
            this.radius = radius;
        }

        public Circle() {
            radius = 0.0;
        }

        public double getRadius() {
            return radius;
        }

        public void setRadius(double radius) {
            this.radius = radius;
        }

        public double getArea() {
            return PI * Math.pow(radius, 2);
        }

        public double getDiameter() {
            return radius * 2;
        }

        public double getCircumference() {
            return 2 * PI * radius;
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter radius: ");
        double radius = Double.parseDouble(br.readLine());

        Circle circle = new Circle(radius);

        System.out.println("Area: " + circle.getArea() + "\nDiameter: " + circle.getDiameter() + "\nCircumference: "
                + circle.getCircumference());
    }
}
