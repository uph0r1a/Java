import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;

public class Ex2 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static class Product {
        private String proId, proName, producer;
        private int yearMaking;
        private float price;

        public Product() {
        }

        public Product(String proId, String proName, String producer, int yearMaking, float price) {
            this.proId = proId;
            this.proName = proName;
            this.producer = producer;
            this.yearMaking = yearMaking;
            this.price = price;
        }

        public String getProId() {
            return proId;
        }

        public void setProId(String proId) {
            this.proId = proId;
        }

        public String getProName() {
            return proName;
        }

        public void setProName(String proName) {
            this.proName = proName;
        }

        public String getProducer() {
            return producer;
        }

        public void setProducer(String producer) {
            this.producer = producer;
        }

        public int getYearMaking() {
            return yearMaking;
        }

        public void setYearMaking(int yearMaking) {
            this.yearMaking = yearMaking;
        }

        public float getPrice() {
            return price;
        }

        public void setPrice(float price) {
            this.price = price;
        }

        @Override
        public String toString() {
            return "\nProduct ID: " + proId + "\nProduct Name: " + proName + "\nProduct Producer: " + producer
                    + "\nYear Making: " + yearMaking + "\nProduct Price: " + price + "\n";
        }
    }

    public static class ProductTest {
        private Map<Integer, Product> productTest;

        public ProductTest() {
            productTest = new HashMap<>();
        }

        public void input() throws IOException {
            System.out.print("Enter number of product: ");
            int n;
            while (true) {
                try {
                    n = Integer.parseInt(br.readLine());
                    if (n > 0) {
                        break;
                    }
                    System.out.print("Invalid number of product\nRe-enter number of product: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            for (int i = 0; i < n; i++) {
                System.out.print("Enter product ID: ");
                String id = br.readLine();

                System.out.print("Enter product name: ");
                String name = br.readLine();

                System.out.print("Enter product producer: ");
                String producer = br.readLine();

                System.out.print("Enter year making: ");
                int yearMaking;
                while (true) {
                    try {
                        yearMaking = Integer.parseInt(br.readLine());
                        if (yearMaking >= 1900 && yearMaking <= Year.now().getValue()) {
                            break;
                        }
                        System.out.print("Invalid year\nRe-enter year making: ");
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                }

                System.out.print("Enter product price: ");
                float price;
                while (true) {
                    try {
                        price = Float.parseFloat(br.readLine());
                        if (price >= 0) {
                            break;
                        }
                        System.out.print("Invalid price\nRe-enter product price: ");
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                }

                productTest.put(productTest.size(), new Product(id, name, producer, yearMaking, price));
            }
        }

        public void display() {
            if (productTest.isEmpty()) {
                System.out.println("No product yet");
            } else {
                for (int i = 0; i < productTest.size(); i++) {
                    System.out.println("Product " + (i + 1) + ": " + productTest.get(i).toString());
                }
            }
        }

        public void sort() {
            if (productTest.isEmpty()) {
                System.out.println("No product yet");
            } else {
                boolean swapped;

                for (int i = 0; i < productTest.size() - 1; i++) {
                    swapped = false;
                    for (int j = 0; j < productTest.size() - i - 1; j++) {
                        if (productTest.get(j).getYearMaking() > productTest.get(j + 1).getYearMaking()) {
                            Product temp = productTest.get(j);
                            productTest.put(j, productTest.get(j + 1));
                            productTest.put(j + 1, temp);
                            swapped = true;
                        }
                    }

                    if (!swapped) {
                        break;
                    }
                }

                display();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        ProductTest productTest = new ProductTest();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    1) Input product
                    2) Display product
                    3) Sorted product by year
                    0) Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 3) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            switch (choice) {
                case 1 -> productTest.input();
                case 2 -> productTest.display();
                case 3 -> productTest.sort();
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}