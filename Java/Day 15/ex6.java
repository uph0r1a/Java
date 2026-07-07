public class ex6 {

    public static int characterCounter(char[] arr, char target, int pos) {
        if (pos == arr.length) {
            return 0;
        }

        int countFromRest = characterCounter(arr, target, pos + 1);

        if (arr[pos] == target) {
            return 1 + countFromRest;
        } else {
            return countFromRest;
        }
    }

    public static int characterCounter(char[] arr, char target) {
        return characterCounter(arr, target, 0);
    }

    public static void main(String[] args) {
        char[] c = { 'a', 'c', 'e', 'a', 'f', 'a', 'c' };

        System.out.println("Count of 'a': " + characterCounter(c, 'a') + "\nCount of 'c': " + characterCounter(c, 'c')
                + "\nCount of 'z': " + characterCounter(c, 'z'));
    }
}