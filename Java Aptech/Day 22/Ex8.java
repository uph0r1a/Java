
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Ex8 {

    // Shared data for both threads
    static class SharedData {
        final Object lock = new Object();
        boolean evensTurn = true;
    }

    // Thread that prints even numbers
    static class EvenThread extends Thread {
        private SharedData data;
        private int n;

        public EvenThread(SharedData data, int n) {
            this.data = data;
            this.n = n;
        }

        @Override
        public void run() {
            for (int i = 0; i <= n; i += 2) {
                synchronized (data.lock) {
                    try {
                        // Wait until it is the even thread's turn
                        while (!data.evensTurn) {
                            data.lock.wait();
                        }

                        System.out.println("Even thread: " + i);

                        // Give the turn to the odd thread
                        data.evensTurn = false;
                        data.lock.notifyAll();

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    // Thread that prints odd numbers
    static class OddThread extends Thread {
        private SharedData data;
        private int n;

        public OddThread(SharedData data, int n) {
            this.data = data;
            this.n = n;
        }

        @Override
        public void run() {
            for (int i = 1; i <= n; i += 2) {
                synchronized (data.lock) {
                    try {
                        // Wait until it is the odd thread's turn
                        while (data.evensTurn) {
                            data.lock.wait();
                        }

                        System.out.println("Odd thread: " + i);

                        // Give the turn to the even thread
                        data.evensTurn = true;
                        data.lock.notifyAll();

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        BufferedReader br =
            new BufferedReader(new InputStreamReader(System.in));

        try {
            // Read n before starting either thread
            System.out.print("Enter a positive integer: ");
            int n = Integer.parseInt(br.readLine());

            if (n <= 0) {
                System.out.println("Please enter a positive integer.");
                return;
            }

            SharedData data = new SharedData();

            // Create both threads
            EvenThread evenThread = new EvenThread(data, n);
            OddThread oddThread = new OddThread(data, n);

            // Start both threads
            evenThread.start();
            oddThread.start();

            // Wait for both threads to finish
            evenThread.join();
            oddThread.join();

            System.out.println("Done");

        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter an integer.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread was interrupted.");
        } catch (Exception e) {
            System.out.println("Input error: " + e.getMessage());
        }
    }
}
