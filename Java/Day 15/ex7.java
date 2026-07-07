public class ex7 {
    public static int power(int num, int exp) {
        if (exp == 0) {
            return 1;
        }
        return num * power(num, exp - 1);
    }

    public static void main(String[] args) {
        System.out.println(power(5, 2));
    }
}
