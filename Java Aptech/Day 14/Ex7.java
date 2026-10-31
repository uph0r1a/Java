import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Arrays;

public class Ex7 {
    public static int linearSearch(int[] array, int target) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == target) {
                return i;
            }
        }

        return -1;
    }

    public static void main(String[] args) throws IOException {
        int[] numbers = { 45, 23, 67, 12, 34, 89, 50 };
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Array: " + Arrays.toString(numbers) + "\nEnter a number to search: ");

        int target = Integer.parseInt(reader.readLine()), result = linearSearch(numbers, target);
        if (result != -1) {
            System.out.println(target + " found at index " + result);
        } else {
            System.out.println(target + " not found.");
        }
    }
}
