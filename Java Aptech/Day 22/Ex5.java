
import java.util.Queue;
import java.util.LinkedList;

public class Ex5 {

    // Thread that performs queue operations
    static class QueueThread extends Thread {
        private Queue<Integer> queue;

        public QueueThread() {
            queue = new LinkedList<>();
        }

        @Override
        public void run() {
            // Add elements to the queue one at a time
            int[] values = {10, 20, 30, 40};

            System.out.println("Adding elements:");

            for (int value : values) {
                queue.add(value);
                System.out.println("Added: " + value);
                System.out.println("Queue: " + queue);
            }

            // Remove elements one at a time
            System.out.println("\nRemoving elements:");

            while (!queue.isEmpty()) {
                int removed = queue.poll();

                System.out.println("Removed: " + removed);
                System.out.println("Queue: " + queue);
            }
        }
    }

    public static void main(String[] args) {
        QueueThread thread = new QueueThread();

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
