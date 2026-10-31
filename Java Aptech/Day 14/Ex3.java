import java.util.Arrays;

public class Ex3 {
    public static void selectionSort(int[] array) {
        int comparisons = 0, swaps = 0, n = array.length;

        for (int i = 0; i < n - 1; i++) {
            int minIndex = i;
            for (int j = i + 1; j < n; j++) {
                comparisons++;
                if (array[j] < array[minIndex]) {
                    minIndex = j;
                }
            }

            if (minIndex != i) {
                int temp = array[i];
                array[i] = array[minIndex];
                array[minIndex] = temp;

                swaps++;
            }
        }

        System.out.println(
                "Sorted Array: " + Arrays.toString(array) + "\nComparisons: " + comparisons + "\nSwaps: " + swaps);
    }

    public static void main(String[] args) {
        int[] numbers = { 45, 11, 32, 5, 27, 19 };
        System.out.println("Original Array: " + Arrays.toString(numbers));
        selectionSort(numbers);
    }
}
