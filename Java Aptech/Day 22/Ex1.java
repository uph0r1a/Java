
import java.util.Random;

public class Ex1 {

    // Shared data between producer and consumer
    static class SharedData {
        int number;
        boolean ready = false;
        final Object lock = new Object();
    }

    // Producer thread
    static class NumberProducer extends Thread {
        private final SharedData data;
        private final Random random = new Random();

        public NumberProducer(SharedData data) {
            this.data = data;
        }

        @Override
        public void run() {
            int number;

            do {
                try {
                    Thread.sleep(5000);

                    // Generate a random number from 1 to 10
                    number = random.nextInt(10) + 1;

                    synchronized (data.lock) {
                        data.number = number;
                        data.ready = true;

                        System.out.println("Produced: " + number);

                        data.lock.notify();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

            } while (number != 10);
        }
    }

    // Consumer thread
    static class WordConsumer extends Thread {
        private final SharedData data;

        private final String[] words = {
            "", "one", "two", "three", "four",
            "five", "six", "seven", "eight",
            "nine", "ten"
        };

        public WordConsumer(SharedData data) {
            this.data = data;
        }

        @Override
        public void run() {
            while (true) {
                synchronized (data.lock) {
                    try {
                        // Wait until a number is available
                        while (!data.ready) {
                            data.lock.wait();
                        }

                        int number = data.number;

                        // Consume the number
                        System.out.println("Consumed: " + words[number]);

                        data.ready = false;

                        // Exit after consuming 10
                        if (number == 10) {
                            break;
                        }

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    // Main method
    public static void main(String[] args) {
        SharedData data = new SharedData();

        NumberProducer producer = new NumberProducer(data);
        WordConsumer consumer = new WordConsumer(data);

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Program finished.");
    }
}
