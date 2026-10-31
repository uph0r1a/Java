import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Arrays;

public class Ex9 {
    public static int binarySearch(int[] array, int target) {
        int low = 0, high = array.length - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;

            if (array[mid] == target) {
                return mid;
            } else if (target < array[mid]) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        return -1;
    }

    public static void main(String[] args) throws IOException {
        int[] numbers = { 3, 6, 9, 12, 15, 18, 21, 24 };
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Array: " + Arrays.toString(numbers) + "\nEnter a number to search: ");

        int target = Integer.parseInt(reader.readLine()), result = binarySearch(numbers, target);
        if (result != -1) {
            System.out.println(target + " found at index " + result);
        } else {
            System.out.println(target + " not found.");
        }
    }
}
