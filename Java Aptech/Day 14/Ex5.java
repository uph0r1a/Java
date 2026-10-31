import java.util.Arrays;

public class Ex5 {
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

            System.out.println("After pass " + (i + 1) + ": " + Arrays.toString(array));
        }
    }

    public static void main(String[] args) {
        int[] numbers = { 29, 10, 14, 37, 13 };

        System.out.println("Original: " + Arrays.toString(numbers));
        selectionSort(numbers);
        System.out.println("Final sorted array: " + Arrays.toString(numbers));
    }
}
