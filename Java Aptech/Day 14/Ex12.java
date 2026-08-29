import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Ex12 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        int[] ID = { 101, 203, 305, 407, 512, 678, 789 };
        System.out.print("Enter product ID to find: ");
        int id = Integer.parseInt(br.readLine());

        System.out.println("Linear Search: ");
        long start = System.currentTimeMillis();
        boolean isFound = false;
        for (int i = 0; i < ID.length; i++) {
            if (id == ID[i]) {
                long time = System.currentTimeMillis() - start;
                isFound = true;
                System.out.println("Found at position " + i + "\nTime: " + time + " ms");
                break;
            }
        }
        if (!isFound) {
            System.out.println("Not found");
        }

        System.out.println("Binary Search: ");
        start = System.currentTimeMillis();
        isFound = false;
        int low = 0, high = ID.length - 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;

            if (ID[mid] == id) {
                long time = System.currentTimeMillis() - start;
                isFound = true;
                System.out.println("Found at position " + mid + "\nTime: " + time + " ms");
                break;
            }
            if (ID[mid] < id) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        if (!isFound) {
            System.out.println("Not found");
        }
    }
}