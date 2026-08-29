import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Ex2 {
    private static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    static int[] arr = new int[10];
    static int size = 10;

    static {
        for (int i = 0; i < size; i++) {
            arr[i] = i + 1;
        }
    }

    public static void printArray() {
        for (int i = 0; i < size; i++) {
            System.out.print(arr[i] + " ");
        }
        System.out.println();
    }

    public static int findMax() {
        int max = arr[0];
        for (int i = 1; i < size; i++) {
            if (arr[i] > max)
                max = arr[i];
        }
        return max;
    }

    public static int findMin() {
        int min = arr[0];
        for (int i = 1; i < size; i++) {
            if (arr[i] < min)
                min = arr[i];
        }
        return min;
    }

    public static int sum() {
        int s = 0;
        for (int i = 0; i < size; i++)
            s += arr[i];
        return s;
    }

    public static double average() {
        return (double) sum() / size;
    }

    public static void add(int x) {
        if (size == arr.length) {
            System.out.println("Array is full!");
            return;
        }
        arr[size++] = x;
    }

    public static void remove(int pos) {
        if (pos < 0 || pos >= size) {
            System.out.println("Invalid position.");
            return;
        }

        for (int i = pos; i < size - 1; i++) {
            arr[i] = arr[i + 1];
        }
        size--;
    }

    public static void insert(int pos, int x) {
        if (pos < 0 || pos > size) {
            System.out.println("Invalid position.");
            return;
        }

        if (size == arr.length) {
            if (pos == size) {
                System.out.println("Array is full!");
                return;
            }
            for (int i = arr.length - 1; i > pos; i--) {
                arr[i] = arr[i - 1];
            }
            arr[pos] = x;
            System.out.println("Array is full! Last element discarded to make room.");
            return;
        }

        for (int i = size; i > pos; i--) {
            arr[i] = arr[i - 1];
        }

        arr[pos] = x;
        size++;
    }

    public static int linearSearch(int x) {
        for (int i = 0; i < size; i++) {
            if (arr[i] == x)
                return i;
        }
        return -1;
    }

    private static void reverse(int[] nums, int start, int end) {
        while (start < end) {
            int temp = nums[start];
            nums[start] = nums[end];
            nums[end] = temp;
            start++;
            end--;
        }
    }

    public static void rotateRight(int k) {
        if (size <= 1)
            return;

        k %= size;

        reverse(arr, 0, size - 1);
        reverse(arr, 0, k - 1);
        reverse(arr, k, size - 1);
    }

    public static void rotateLeft(int k) {
        if (size <= 1)
            return;

        k %= size;

        reverse(arr, 0, k - 1);
        reverse(arr, k, size - 1);
        reverse(arr, 0, size - 1);
    }

    public static void reverseArray() {
        reverse(arr, 0, size - 1);
    }

    public static boolean isPalindrome() {
        int left = 0;
        int right = size - 1;

        while (left < right) {
            if (arr[left] != arr[right])
                return false;
            left++;
            right--;
        }

        return true;
    }

    public static void bubbleSort() {
        for (int i = 0; i < size - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < size - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                    swapped = true;
                }
            }
            if (!swapped)
                break;
        }
    }

    public static void selectionSort() {
        for (int i = 0; i < size - 1; i++) {
            int min = i;

            for (int j = i + 1; j < size; j++) {
                if (arr[j] < arr[min])
                    min = j;
            }

            int temp = arr[min];
            arr[min] = arr[i];
            arr[i] = temp;
        }
    }

    private static int readInt() throws IOException {
        while (true) {
            try {
                return Integer.parseInt(br.readLine().trim());
            } catch (NumberFormatException e) {
                System.out.print("Invalid number! Enter again: ");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Original array:");
        printArray();

        System.out.println("\nMaximum = " + findMax() + "\nMinimum = " + findMin() + "\nSum = " + sum() + "\nAverage = "
                + average());

        System.out.println("\nRemove position 3:");
        remove(3);
        printArray();

        System.out.println("\nAdd 11 (should succeed, one free slot):");
        add(11);
        printArray();

        System.out.println("\nAdd 99 (should fail, array is full again):");
        add(99);
        printArray();

        System.out.println("\nInsert 100 at position 2 (array is full, last element should be dropped):");
        insert(2, 100);
        printArray();

        int x = 7;
        int pos = linearSearch(x);
        if (pos != -1)
            System.out.println("\n" + x + " found at index " + pos);
        else
            System.out.println("\n" + x + " not found.");

        System.out.print("\nEnter k for rotation: ");
        int k = readInt();

        System.out.println("Rotate left by " + k + ":");
        rotateLeft(k);
        printArray();

        System.out.println("\nRotate right by " + k + ":");
        rotateRight(k);
        printArray();

        System.out.println("\nReverse:");
        reverseArray();
        printArray();

        System.out.println("\nPalindrome? " + isPalindrome());

        System.out.println("\nReverse again to unsort, then bubble sort:");
        reverseArray();
        bubbleSort();
        printArray();

        System.out.println("\nReverse again to unsort, then selection sort:");
        reverseArray();
        selectionSort();
        printArray();
    }
}