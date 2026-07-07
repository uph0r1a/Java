public class ex5 {
    public static void largerThanN(int[] arr, int n) {
        System.out.print("Larger than n: ");
        for (int i : arr) {
            if (i > n) {
                System.out.print(i + " ");
            }
        }
    }

    public static void main(String[] args) {
        int[] arr = { 6, 3, 6, 7, 5, 4, 2, 5, 6, 8, 3, 2, 56, 3, 1 };
        int n = 6;
        largerThanN(arr, n);
    }
}
