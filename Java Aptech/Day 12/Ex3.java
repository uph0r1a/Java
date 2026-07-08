import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Ex3 {
    public static class Product {
        private String maSP, tenSP;
        private int soLuongTon;
        private double giaban;

        public Product(String maSP, String tenSP, int soLuongTon, double giaban) {
            this.maSP = maSP;
            this.tenSP = tenSP;
            this.soLuongTon = soLuongTon;
            this.giaban = giaban;
        }

        public String getMaSP() {
            return maSP;
        }

        public String getTenSP() {
            return tenSP;
        }

        public int getSoLuongTon() {
            return soLuongTon;
        }

        public double getGiaban() {
            return giaban;
        }

        public void setMaSP(String maSP) {
            this.maSP = maSP;
        }

        public void setTenSP(String tenSP) {
            this.tenSP = tenSP;
        }

        public void setSoLuongTon(int soLuongTon) {
            this.soLuongTon = soLuongTon;
        }

        public void setGiaban(double giaban) {
            this.giaban = giaban;
        }

    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String, Product> storage = new HashMap<>();
        boolean isExit = false;
        while (!isExit) {
            System.out.print("""
                    1.  Import
                    2.  Export
                    3.  Search for product
                    4.  Low stock product
                    5.  Total value
                    0.  Exit
                    Enter your choice:\s""");

            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 5) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (choice) {
                case 1 -> {
                    System.out.print("Enter product ID: ");
                    String maSP = br.readLine().strip();
                    String tenSP;
                    double giaBan;

                    if (!storage.containsKey(maSP)) {
                        System.out.print("Enter product name: ");
                        tenSP = br.readLine().strip();

                        System.out.print("Enter product price: ");
                        while (true) {
                            try {
                                giaBan = Double.parseDouble(br.readLine());
                                if (giaBan >= 0) {
                                    break;
                                }
                                System.out.print("Price cant be negative\nRe-enter price: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }
                    } else {
                        tenSP = storage.get(maSP).getTenSP();
                        giaBan = storage.get(maSP).getGiaban();
                    }

                    System.out.print("Enter product stock to import: ");
                    int soLuongTon;
                    while (true) {
                        try {
                            soLuongTon = Integer.parseInt(br.readLine());
                            if (soLuongTon >= 0) {
                                break;
                            }
                            System.out.print("Stock cant be negative\nRe-enter stock: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    Product product = new Product(maSP, tenSP,
                            storage.containsKey(maSP) ? storage.get(maSP).getSoLuongTon() + soLuongTon : soLuongTon,
                            giaBan);
                    storage.put(product.getMaSP(), product);
                }
                case 2 -> {
                    if (!storage.isEmpty()) {
                        System.out.print("Enter product ID: ");
                        String maSP;
                        while (true) {
                            try {
                                maSP = br.readLine().strip();
                                if (storage.containsKey(maSP)) {
                                    break;
                                }
                                System.out.print("Invalid ID\nRe-enter product ID: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }

                        System.out.print("Enter product stock to export: ");
                        int soLuongTon;
                        while (true) {
                            try {
                                soLuongTon = Integer.parseInt(br.readLine());
                                if (soLuongTon >= 0 && storage.get(maSP).getSoLuongTon() - soLuongTon >= 0) {
                                    break;
                                }
                                System.out.print("Not enough stock or invalid quantity\nRe-enter stock: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }

                        Product product = storage.get(maSP);
                        product.setSoLuongTon(product.getSoLuongTon() - soLuongTon);
                        storage.put(maSP, product);
                    } else {
                        System.out.println("No product yet");
                    }
                }
                case 3 -> {
                    if (!storage.isEmpty()) {
                        System.out.print("Enter product name: ");
                        String tenSP = br.readLine().strip();
                        boolean found = false;

                        for (Product value : storage.values()) {
                            if (value.getTenSP().toLowerCase().contains(tenSP.toLowerCase())) {
                                found = true;
                                System.out.println(
                                        "Product ID: " + value.getMaSP() + "\nProduct name: " + value.getTenSP()
                                                + "\nProduct stock: " + value.getSoLuongTon() + "\nProduct price: "
                                                + value.getGiaban() + "\n");
                            }
                        }

                        if (!found) {
                            System.out.println("No product matches \"" + tenSP + "\".\n");
                        }
                    } else {
                        System.out.println("No product yet");
                    }
                }
                case 4 -> {
                    if (!storage.isEmpty()) {
                        boolean found = false;

                        for (Product value : storage.values()) {
                            if (value.getSoLuongTon() < 10) {
                                found = true;
                                System.out.println(
                                        "Product ID: " + value.getMaSP() + "\nProduct name: " + value.getTenSP()
                                                + "\nProduct stock: " + value.getSoLuongTon() + "\nProduct price: "
                                                + value.getGiaban());
                            }
                        }

                        if (!found) {
                            System.out.println("No product is low on stock.");
                        }
                    } else {
                        System.out.println("No product yet");
                    }
                }
                case 5 -> {
                    if (!storage.isEmpty()) {
                        System.out.println("Total product value: "
                                + storage.values().stream().mapToDouble(p -> p.getGiaban() * p.getSoLuongTon()).sum());
                    } else {
                        System.out.println("No product yet");
                    }
                }
                case 0 -> isExit = true;
                default -> System.out.println("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}