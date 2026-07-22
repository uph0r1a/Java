public class Ex2 {
    static int[] arr = {1,2,3,4,5,6,7,8,9,10};
    public static void main(String[] args) {
        int max = Integer.MIN_VALUE, min = Integer.MAX_VALUE,sum = 0, average = 0;
        for (int  n : arr) {
            System.out.println(n + "\t");
            if (n > max) {
                max = n;
            }
            if (n < min) {
                min = n;
            }
            sum += n;
        }
        
    }
}
