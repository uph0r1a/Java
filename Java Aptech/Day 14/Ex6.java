import java.util.Arrays;

public class Ex6 {
    public static void insertionSort(int[] array) {
        int comparisons = 0, shifts = 0;

        for (int i = 1; i < array.length; i++) {
            int key = array[i], j = i - 1;

            while (j >= 0) {
                comparisons++;

                if (array[j] > key) {
                    array[j + 1] = array[j];
                    shifts++;
                    j--;
                } else {
                    break;
                }
            }

            array[j + 1] = key;
        }

        System.out.println(
                "Insertion Sort: " + Arrays.toString(array) + "\nComparisons: " + comparisons + "\nShifts: " + shifts);
    }

    static int mergeComparisons = 0;

    public static void mergeSort(int[] array, int left, int right) {
        if (left < right) {
            int middle = left + (right - left) / 2;

            mergeSort(array, left, middle);
            mergeSort(array, middle + 1, right);
            merge(array, left, middle, right);
        }
    }

    public static void merge(int[] array, int left, int middle, int right) {
        int n1 = middle - left + 1, n2 = right - middle;

        int[] leftArray = new int[n1], rightArray = new int[n2];

        for (int i = 0; i < n1; i++) {
            leftArray[i] = array[left + i];
        }

        for (int j = 0; j < n2; j++) {
            rightArray[j] = array[middle + 1 + j];
        }

        int i = 0, j = 0, k = left;

        while (i < n1 && j < n2) {
            mergeComparisons++;

            if (leftArray[i] <= rightArray[j]) {
                array[k] = leftArray[i];
                i++;
            } else {
                array[k] = rightArray[j];
                j++;
            }

            k++;
        }

        while (i < n1) {
            array[k] = leftArray[i];
            i++;
            k++;
        }

        while (j < n2) {
            array[k] = rightArray[j];
            j++;
            k++;
        }
    }

    public static void main(String[] args) {
        int[] originalArray = { 45, 11, 32, 5, 27, 19 },
                insertionArray = Arrays.copyOf(originalArray, originalArray.length),
                mergeArray = Arrays.copyOf(originalArray, originalArray.length);

        System.out.println("Original Array: " + Arrays.toString(originalArray) + "\n--- Insertion Sort ---");
        insertionSort(insertionArray);

        System.out.println("\n--- Merge Sort ---");
        mergeSort(mergeArray, 0, mergeArray.length - 1);

        System.out.println("Merge Sort: " + Arrays.toString(mergeArray) + "\nComparisons: " + mergeComparisons
                + "\nSwaps: Not applicable");
    }
}
