public class ex4 {

    public static int maxElement(int[] arr, int max, int pos) {
        if (pos == arr.length) {
            return max;
        }
        if (arr[pos] > max) {
            max = arr[pos];
        }
        return maxElement(arr, max, pos + 1);
    }

    public static int maxElement(int[] arr) {
        return maxElement(arr, arr[0], 0);
    }

    public static void main(String[] args) {
        int[] arr = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 2, -5, -2, -10, -1 };
        System.out.println("Max: " + maxElement(arr));
    }
}