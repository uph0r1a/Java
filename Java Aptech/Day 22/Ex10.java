
import java.util.Random;

public class Ex10 {

    // Day names in both languages
    static class DayData {
        String[] englishDays = {
            "Monday", "Tuesday", "Wednesday",
            "Thursday", "Friday", "Saturday", "Sunday"
        };

        String[] vietnameseDays = {
            "Thu hai", "Thu ba", "Thu tu",
            "Thu nam", "Thu sau", "Thu bay", "Chu nhat"
        };

        final Object lock = new Object();
        int dayIndex;
        boolean ready = false;
    }

    // Producer thread
    static class DayProducer extends Thread {
        private DayData data;
        private Random random = new Random();

        public DayProducer(DayData data) {
            this.data = data;
        }

        @Override
        public void run() {
            try {
                // Wait for 1 second
                Thread.sleep(1000);

                // Select a random day (index 0 to 6)
                int index = random.nextInt(7);

                synchronized (data.lock) {
                    // Store the selected index
                    data.dayIndex = index;

                    // Print the Vietnamese day
                    System.out.println(
                        "Produced: " + data.vietnameseDays[index]
                    );

                    // Notify the consumer
                    data.ready = true;
                    data.lock.notify();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Producer interrupted.");
            }
        }
    }

    // Consumer thread
    static class DayConsumer extends Thread {
        private DayData data;

        public DayConsumer(DayData data) {
            this.data = data;
        }

        @Override
        public void run() {
            synchronized (data.lock) {
                try {
                    // Wait until a day is available
                    while (!data.ready) {
                        data.lock.wait();
                    }

                    // Read the produced index
                    int index = data.dayIndex;

                    // Print the English equivalent
                    System.out.println(
                        "Consumed: " + data.englishDays[index]
                    );

                    // Mark the data as consumed
                    data.ready = false;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Consumer interrupted.");
                }
            }
        }
    }

    public static void main(String[] args) {
        DayData data = new DayData();

        // Create both threads
        DayProducer producer = new DayProducer(data);
        DayConsumer consumer = new DayConsumer(data);

        // Start both threads
        producer.start();
        consumer.start();

        try {
            // Wait for both threads to finish
            producer.join();
            consumer.join();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread was interrupted.");
        }
    }
}
