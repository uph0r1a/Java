public class Ex8 {
    public static class Point {
        private double x, y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public void setX(double x) {
            this.x = x;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public String displayPoints() {
            return "Point (" + x + ", y" + y + ")";
        }
    }

    public static void main(String[] args) {
        Point point1 = new Point(0, 0), point2 = new Point(1, 1);

        if (point1.getX() == point2.getX() && point1.getY() == point2.getY()) {
            System.out.println("The points are the same");
        } else {
            System.out.println("The points are different");
        }
    }
}
