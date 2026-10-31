import java.util.Arrays;

public class Ex1 {
    public static int recursiveSum(int[] array, int index) {
        if (index == array.length) {
            return 0;
        }
        return array[index] + recursiveSum(array, index + 1);
    }

    public static void main(String[] args) {
        int[] numbers = { 3, 5, 7, 9 };
        int sum = recursiveSum(numbers, 0);

        System.out.println("Array: " + Arrays.toString(numbers) + "\nSum: " + sum);
    }
}
