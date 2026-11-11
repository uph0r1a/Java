
import java.util.Arrays;

public class Ex7 {

    // Thread that holds the shared array
    static class ArrayHolderThread extends Thread {
        private int[] numList = {1, 3, 4, 6, 7};

        // Getter to expose the shared array
        public int[] getNumList() {
            return numList;
        }

        @Override
        public void run() {
            System.out.println("Array is ready.");
        }
    }

    // Thread that modifies the shared array
    static class ArrayModifierThread extends Thread {
        private int[] numList;

        // Constructor receives the shared array reference
        public ArrayModifierThread(int[] numList) {
            this.numList = numList;
        }

        @Override
        public void run() {
            // Add 10 to every element
            for (int i = 0; i < numList.length; i++) {
                numList[i] += 10;
            }

            System.out.println("Array modified.");
        }
    }

    public static void main(String[] args) {
        // Create and start the holder thread
        ArrayHolderThread holder = new ArrayHolderThread();
        holder.start();

        try {
            // Wait until the holder thread finishes
            holder.join();

            // Pass the shared array to the modifier thread
            ArrayModifierThread modifier =
                new ArrayModifierThread(holder.getNumList());

            // Start the modifier thread
            modifier.start();

            // Wait until modification finishes
            modifier.join();

            // Print the final array
            System.out.println(Arrays.toString(holder.getNumList()));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread was interrupted.");
        }
    }
}
