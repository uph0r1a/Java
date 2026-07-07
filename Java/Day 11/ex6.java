import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class ex6 {
    static class FileArray {

        public static void writeArray(String fileName, int[] arr) {
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(fileName))) {
                for (int value : arr) {
                    out.writeInt(value);
                }
            } catch (IOException e) {
                System.out.println("Error writing file: " + e.getMessage());
            }
        }

        public static void readArray(String fileName, int[] arr) {
            try (DataInputStream in = new DataInputStream(new FileInputStream(fileName))) {
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = in.readInt();
                }
            } catch (IOException e) {
                System.out.println("Error reading file: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        int[] original = { 1, 2, 0, 4, 2, 3, 4, 5, 3, 4, 6, 7, 8, 6, 5, 8, 9, 4, 1 };
        int[] restored = new int[original.length];

        FileArray.writeArray("files/arrays.bin", original);
        FileArray.readArray("files/arrays.bin", restored);

        System.out.println("Original: " + Arrays.toString(original) + "\nRestored: " + Arrays.toString(restored)
                + "\nMatch: " + Arrays.equals(original, restored));
    }
}