public class ex8 {
    public static void main(String[] args) {
        String str = "7,9,10,2,18,6";
        String[] parts = str.split(",");
        int sum = 0;

        for (String part : parts) {
            sum += Integer.parseInt(part);
        }
        System.out.println("Sum: " + sum);
    }
}
