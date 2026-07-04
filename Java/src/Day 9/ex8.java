public class ex8 {
    public static void main(String[] args) {
        String str = "7,9,10,2,18,6";
        String[] parts = str.split(",");
        int sum = 0;

        for (int i = 0; i < parts.length; i++) {
            sum += Integer.parseInt(parts[i]);
        }
        System.out.println("Sum: " + sum);
    }
}
