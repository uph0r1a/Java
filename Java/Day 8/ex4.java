import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex4 {
    static class LandTract {
        private final double length, width;

        public LandTract(double length, double width) {
            this.length = length;
            this.width = width;
        }

        public double getArea() {
            return length * width;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            LandTract land = (LandTract) o;
            return Double.compare(this.getArea(), land.getArea()) == 0;
        }

        @Override
        public String toString() {
            return "Length: " + length + "\nWidth: " + width + "\nArea: " + getArea();
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter length of area 1: ");
        double length1 = Double.parseDouble(br.readLine());

        System.out.print("Enter width of area 1: ");
        double width1 = Double.parseDouble(br.readLine());

        System.out.print("Enter length of area 2: ");
        double length2 = Double.parseDouble(br.readLine());

        System.out.print("Enter width of area 2: ");
        double width2 = Double.parseDouble(br.readLine());

        LandTract land1 = new LandTract(length1, width1);
        LandTract land2 = new LandTract(length2, width2);

        System.out.println("Area 1: " + land1.getArea() + "\nArea 2: " + land2.getArea()
                + (land1.equals(land2) ? "\nEqual" : "\nNot equal"));
    }
}
