public class ex2 {
    public static boolean isMember(int[] array, int value, int index) {
        if (index >= array.length) {
            return false;
        }

        if (array[index] == value) {
            return true;
        }
        return isMember(array, value, index + 1);
    }

    public static boolean isMember(int[] array, int value) {
        return isMember(array, value, 0);
    }

    public static void main(String[] args) {
        int[] numbers = { 4, 8, 15, 16, 23, 42 };

        System.out.println(isMember(numbers, 16) ? "Contain" : "Not contain");
    }
}