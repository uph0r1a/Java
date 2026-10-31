import java.util.Arrays;

public class Ex2 {
    public static void selectionSort(int[] array) {
        int n = array.length;
        for (int i = 0; i < n - 1; i++) {
            int minIndex = i;
            for (int j = i + 1; j < n; j++) {
                if (array[j] < array[minIndex]) {
                    minIndex = j;
                }
            }

            int temp = array[i];
            array[i] = array[minIndex];
            array[minIndex] = temp;
        }
    }

    public static void main(String[] args) {
        int[] numbers = { 34, 12, 25, 9, 78, 31 };
        System.out.println("Original Array: " + Arrays.toString(numbers));
        selectionSort(numbers);
        System.out.println("Sorted Array:   " + Arrays.toString(numbers));
    }
}
