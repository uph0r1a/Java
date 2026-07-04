public class ex1 {
    public static String reverse(String str) {
        return new StringBuilder(str).reverse().toString();
    }

    public static void main(String[] args) {
        String str = "gravity";
        System.out.println(reverse(str));
    }
}
