
public class Ex2 {

    // Thread that calculates the sum of an array
    static class SumThread extends Thread {
        private int[] data;
        private int result;

        // Constructor
        public SumThread(int[] data) {
            this.data = data;
        }

        @Override
        public void run() {
            int sum = 0;

            for (int number : data) {
                sum += number;
            }

            result = sum;
        }

        public int getResult() {
            return result;
        }
    }

    public static void main(String[] args) {
        // Create the array
        int[] numbers = {4, 8, 15, 16, 23, 42};

        // Create and start the thread
        SumThread sumThread = new SumThread(numbers);
        sumThread.start();

        try {
            // Wait for the thread to finish
            sumThread.join();

            // Read and print the result
            System.out.println("Sum: " + sumThread.getResult());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread was interrupted.");
        }
    }
}
