public class ex1 {
    public static int multiplication(int x, int y) {
        if (y == 1) {
            return x;
        }
        return x + multiplication(x, y - 1);
    }

    public static void main(String[] args) {
        System.out.println(multiplication(10, 10));
    }
}
