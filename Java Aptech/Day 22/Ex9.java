
public class Ex9 {

    // Shared data for both threads
    static class SharedData {
        final Object lock = new Object();
        boolean uppercaseTurn = true;
    }

    // Thread that prints uppercase letters
    static class UppercaseThread extends Thread {
        private SharedData data;

        public UppercaseThread(SharedData data) {
            this.data = data;
        }

        @Override
        public void run() {
            for (char letter = 'A'; letter <= 'Z'; letter++) {
                synchronized (data.lock) {
                    try {
                        // Wait until it is the uppercase thread's turn
                        while (!data.uppercaseTurn) {
                            data.lock.wait();
                        }

                        System.out.println(letter);

                        // Give the turn to the lowercase thread
                        data.uppercaseTurn = false;
                        data.lock.notifyAll();

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    // Thread that prints lowercase letters
    static class LowercaseThread extends Thread {
        private SharedData data;

        public LowercaseThread(SharedData data) {
            this.data = data;
        }

        @Override
        public void run() {
            for (char letter = 'a'; letter <= 'z'; letter++) {
                synchronized (data.lock) {
                    try {
                        // Wait until it is the lowercase thread's turn
                        while (data.uppercaseTurn) {
                            data.lock.wait();
                        }

                        System.out.println(letter);

                        // Give the turn back to the uppercase thread
                        data.uppercaseTurn = true;
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
        SharedData data = new SharedData();

        // Create both threads
        UppercaseThread uppercaseThread = new UppercaseThread(data);
        LowercaseThread lowercaseThread = new LowercaseThread(data);

        // Start both threads
        uppercaseThread.start();
        lowercaseThread.start();

        try {
            // Wait for both threads to finish
            uppercaseThread.join();
            lowercaseThread.join();

            System.out.println("Done");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread was interrupted.");
        }
    }
}
