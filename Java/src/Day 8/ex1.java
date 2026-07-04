public class ex1 {
    static class Area {
        public static double area(double r) {
            return Math.PI * Math.pow(r, 2);
        }

        public static double area(double w, double l) {
            return w * l;
        }

        public static double area(double r, double h, boolean isCylinder) {
            return Math.PI * Math.pow(r, 2) * h;
        }
    }

    public static void main(String[] args) {
        System.out.println(
                "Circle: " + Area.area(5) + "\nRectangle: " + Area.area(4, 6) + "\nCylinder: " + Area.area(3, 8, true));
    }
}
