
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Ex3 {

    // Thread that prints numbers in random order
    static class RandomOrderThread extends Thread {
        private List<Integer> numbers;

        public RandomOrderThread() {
            numbers = new ArrayList<>();

            // Add numbers from 1 to 10
            for (int i = 1; i <= 10; i++) {
                numbers.add(i);
            }

            // Shuffle the list once
            Collections.shuffle(numbers);
        }

        @Override
        public void run() {
            // Print each number on its own line
            for (int number : numbers) {
                System.out.println(number);
            }
        }
    }

    public static void main(String[] args) {
        RandomOrderThread thread = new RandomOrderThread();

        // Start the thread
        thread.start();

        try {
            // Wait for the thread to finish
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread was interrupted.");
        }
    }
}
