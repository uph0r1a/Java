public class Ex2 {
    static int[] arr = new int[20];
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
        if (size == arr.length) {
            System.out.println("Array is full!");
            return;
        }

        if (pos < 0 || pos > size) {
            System.out.println("Invalid position.");
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

    public static void main(String[] args) {
        System.out.println("Original array:");
        printArray();

        System.out.println("\nMaximum = " + findMax() + "\nMinimum = " + findMin() + "\nSum = " + sum() + "\nAverage = "
                + average() + "\n\nAdd 11:");
        add(11);
        printArray();

        System.out.println("\nRemove position 3:");
        remove(3);
        printArray();

        System.out.println("\nInsert 100 at position 2:");
        insert(2, 100);
        printArray();

        int x = 7;
        int pos = linearSearch(x);
        if (pos != -1)
            System.out.println("\n" + x + " found at index " + pos);
        else
            System.out.println("\n" + x + " not found.");

        System.out.println("\nRotate left by 2:");
        rotateLeft(2);
        printArray();

        System.out.println("\nRotate right by 2:");
        rotateRight(2);
        printArray();

        System.out.println("\nReverse:");
        reverseArray();
        printArray();

        System.out.println("\nPalindrome? " + isPalindrome());

        System.out.println("\nSelection sort:");
        selectionSort();
        printArray();
    }
}