import java.util.concurrent.ThreadLocalRandom;

public class Ex1 {
    public static void main(String[] args) {
        Thread randomNumberThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                System.out.println(ThreadLocalRandom.current().nextInt(1, 101));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        randomNumberThread.start();
    }
}