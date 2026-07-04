import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex3 {
    static class RoomDimension {
        private double length, width;

        public RoomDimension(double length, double width) {
            this.length = length;
            this.width = width;
        }

        public double getArea() {
            return length * width;
        }

        @Override
        public String toString() {
            return "\nLength: " + length + "\nWidth: " + width + "\nArea: " + getArea();
        }
    }

    static class RoomCarpet {
        private RoomDimension size;
        private double carpetCost;

        public RoomCarpet(RoomDimension size, double carpetCost) {
            this.size = size;
            this.carpetCost = carpetCost;
        }

        public double getTotalCost() {
            return size.getArea() * carpetCost;
        }

        @Override
        public String toString() {
            return "\nSize: " + size.getArea() + "\nCarpetCost: " + carpetCost + "\nTotal cost: " + getTotalCost();
        }

    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter width: ");
        double width = Double.parseDouble(br.readLine());

        System.out.print("Enter length: ");
        double length = Double.parseDouble(br.readLine());

        System.out.print("Enter cost: ");
        double cost = Double.parseDouble(br.readLine());

        RoomCarpet carpet = new RoomCarpet(new RoomDimension(length, width), cost);

        System.out.println("Total cost: " + carpet.getTotalCost());
    }
}
