
public class Ex6 {

    // Thread that prints elapsed time
    static class TimerThread extends Thread {

        @Override
        public void run() {
            // Record the start time before the loop
            long startTime = System.currentTimeMillis();

            // Print elapsed time 10 times
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(1000);

                    // Calculate elapsed time in seconds
                    long elapsedTime =
                        (System.currentTimeMillis() - startTime) / 1000;

                    System.out.println("Elapsed: " + elapsedTime + "s.");

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Timer interrupted.");
                    return;
                }
            }
        }
    }

    public static void main(String[] args) {
        TimerThread timer = new TimerThread();

        // Start the timer thread
        timer.start();

        try {
            // Wait for the timer thread to finish
            timer.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread was interrupted.");
        }
    }
}
