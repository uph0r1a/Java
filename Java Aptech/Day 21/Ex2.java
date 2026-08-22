public class Ex2 {
    public static void main(String[] args) {
        Thread provinceThread = new Thread(() -> {
            String[] provinces = { "Hanoi", "Haiphong", "Quang Ninh", "Thanh Hoa", "Nghe An", "Da Nang", "Quang Nam",
                    "Khanh Hoa", "Lam Dong", "Binh Duong", "Dong Nai", "Ba Ria - Vung Tau", "Long An", "An Giang",
                    "Kien Giang", "Can Tho", "Ca Mau" };

            for (String province : provinces) {
                System.out.println(province);
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        });
        provinceThread.start();
    }
}
