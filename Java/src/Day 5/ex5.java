public class ex5 {
    public static double fallingDistance(int time) {
        return 0.5 * 9.8 * Math.pow(time, 2);
    }

    public static void main(String[] args) {
        for (int i = 1; i <= 10; i++) {
            System.out.printf("%.1f%n", fallingDistance(i));
        }
    }
}
