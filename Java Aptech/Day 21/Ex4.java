import java.util.concurrent.ThreadLocalRandom;

public class Ex4 {
    public static void main(String[] args) {
        String[] arrSinhVien = { "Hoàng", "Tuấn", "Quỳnh", "Trang", "Vũ" };
        String[] arrHanhDong = { "...đang ăn", "...đang ngủ", "...đang ị", "...đang làm bài tập", "...đang học" };

        Thread a = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                System.out.println(arrSinhVien[ThreadLocalRandom.current().nextInt(0, arrSinhVien.length)]);
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        Thread b = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                System.out.println(arrHanhDong[ThreadLocalRandom.current().nextInt(0, arrHanhDong.length)]);
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        a.start();
        b.start();
    }
}