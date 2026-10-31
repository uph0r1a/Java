import java.util.Arrays;

public class Ex1 {
    public static void bubbleSort(int[] array) {
        int n = array.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (array[j] > array[j + 1]) {
                    int temp = array[j];
                    array[j] = array[j + 1];
                    array[j + 1] = temp;
                }
            }
        }
    }

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
        int[] originalArray = { 64, 25, 12, 22, 11 }, bubbleArray = Arrays.copyOf(originalArray, originalArray.length),
                selectionArray = Arrays.copyOf(originalArray, originalArray.length);

        bubbleSort(bubbleArray);
        selectionSort(selectionArray);

        System.out.println("Original Array: " + Arrays.toString(originalArray) + "\nBubble Sort: "
                + Arrays.toString(bubbleArray) + "\nSelection Sort: " + Arrays.toString(selectionArray));
    }
}
