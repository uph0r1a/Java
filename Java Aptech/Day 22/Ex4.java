
public class Ex4 {

    // Thread that performs the countdown
    static class CountdownThread extends Thread {

        @Override
        public void run() {
            for (int i = 10; i >= 1; i--) {
                System.out.println(i);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Countdown interrupted.");
                    return;
                }
            }

            System.out.println("Liftoff!");
        }
    }

    public static void main(String[] args) {
        CountdownThread countdown = new CountdownThread();

        // Start the countdown thread
        countdown.start();

        try {
            // Wait for the countdown to finish
            countdown.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread was interrupted.");
        }
    }
}
