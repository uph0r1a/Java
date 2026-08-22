import java.util.concurrent.ThreadLocalRandom;

public class Ex4 {
    public static void main(String[] args) {
        String[] arrSinhVien = { "Hoàng", "Tuấn", "Quỳnh", "Trang", "Vũ" },
                arrHanhDong = { "...đang ăn", "...đang ngủ", "...đang ị", "...đang làm bài tập", "...đang học" };

        Thread a = new Thread(() -> {
            System.out.print(arrSinhVien[ThreadLocalRandom.current().nextInt(0, arrSinhVien.length)]);
            try {
                Thread.sleep(1200);
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }), b = new Thread(() -> {
            System.out.print(arrHanhDong[ThreadLocalRandom.current().nextInt(0, arrHanhDong.length)]);
            try {
                Thread.sleep(1200);
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        });

        a.start();
        b.start();
    }
}