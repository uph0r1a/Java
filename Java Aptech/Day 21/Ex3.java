import java.time.LocalTime;
import java.util.concurrent.ThreadLocalRandom;

public class Ex3 {
    public static void main(String[] args) {
        Thread randomNumberThread = new Thread(() -> {
            System.out.println(ThreadLocalRandom.current().nextInt());
        });
        Thread localTimeThread = new Thread(() -> {
            System.out.println(LocalTime.now());
        });

        randomNumberThread.start();
        localTimeThread.start();
    }
}
