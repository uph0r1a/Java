import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ex9 {
    static class Geometry {
        private double radius, length, width, base, height;

        public Geometry(double radius) {
            this.radius = radius;
        }

        public Geometry(double length, double width) {
            this.length = length;
            this.width = width;
        }

        public Geometry(double base, double height, boolean isTriangle) {
            this.base = base;
            this.height = height;
        }

        public double circleArea() {
            return Math.PI * Math.pow(radius, 2);
        }

        public double rectangleArea() {
            return length * width;
        }

        public double triangleArea() {
            return base * height * 0.5;
        }
    }

    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        boolean isExit = false;
        while (!isExit) {
            System.out.print(
                    "Geometry Calculator\n1. Calculate the Area of a Circle\n2. Calculate the Area of a Rectangle\n3. Calculate the Area of a Triangle\n4. Quit\nEnter your choice (1-4):");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 1 && choice <= 4) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            switch (choice) {
                case 1:
                    System.out.print("Enter radius: ");
                    double radius;
                    while (true) {
                        try {
                            radius = Double.parseDouble(br.readLine());
                            if (radius >= 0) {
                                break;
                            }
                            System.out.println("Radius cant be negative\nRe-enter radius: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    Geometry circle = new Geometry(radius);
                    System.out.println("Circle area: " + circle.circleArea());
                    break;
                case 2:
                    System.out.print("Enter length: ");
                    double length;
                    while (true) {
                        try {
                            length = Double.parseDouble(br.readLine());
                            if (length >= 0) {
                                break;
                            }
                            System.out.println("Length cant be negative\nRe-enter length: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter width: ");
                    double width;
                    while (true) {
                        try {
                            width = Double.parseDouble(br.readLine());
                            if (width >= 0) {
                                break;
                            }
                            System.out.println("Width cant be negative\nRe-enter width: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }
                    Geometry rectangle = new Geometry(length, width);
                    System.out.println("Rectangle area: " + rectangle.rectangleArea());
                    break;
                case 3:
                    System.out.print("Enter base: ");
                    double base;
                    while (true) {
                        try {
                            base = Double.parseDouble(br.readLine());
                            if (base >= 0) {
                                break;
                            }
                            System.out.println("Base cant be negative\nRe-enter base: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter height: ");
                    double height;
                    while (true) {
                        try {
                            height = Double.parseDouble(br.readLine());
                            if (height >= 0) {
                                break;
                            }
                            System.out.println("Height cant be negative\nRe-enter height: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }
                    Geometry triangle = new Geometry(base, height, true);
                    System.out.println("Triangle area: " + triangle.triangleArea());
                    break;
                case 4:
                    isExit = true;
                    break;
                default:
                    System.out.print("Invalid choice\nRe-enter choice: ");
                    break;
            }
        }
    }
}
