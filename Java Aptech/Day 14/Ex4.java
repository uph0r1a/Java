import java.util.Arrays;

public class Ex4 {
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
        int[] numbers = { 29, 10, 14, 37, 13 };

        System.out.println("Original Array: " + Arrays.toString(numbers));
        selectionSort(numbers);
        System.out.println("Sorted Array: " + Arrays.toString(numbers));
    }
}
