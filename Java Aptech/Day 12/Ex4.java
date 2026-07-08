import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class Ex4 {
    public static class CartItem {
        private String item;
        private int stock;

        public CartItem(String item, int stock) {
            this.item = item;
            this.stock = stock;
        }

        public String getItem() {
            return item;
        }

        public void setItem(String item) {
            this.item = item;
        }

        public int getStock() {
            return stock;
        }

        public void setStock(int stock) {
            this.stock = stock;
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String, CartItem> items = new HashMap<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    1.  Add Item
                    2.  Remove item
                    3.  Total cost
                    4.  Cart detail
                    0.  Exit
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 4) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage() + "\nRe-enter choice: ");
                }
            }

            switch (choice) {
                case 1 -> {
                    System.out.print("Enter product ID: ");
                    String id = br.readLine();
                    String name;
                    if (!items.containsKey(id)) {
                        System.out.print("Enter item name: ");
                        name = br.readLine();
                    } else {
                        name = items.get(id).getItem();
                    }

                    System.out.print("Enter item stock to add: ");
                    int stock;
                    while (true) {
                        try {
                            stock = Integer.parseInt(br.readLine());
                            if (stock >= 0) {
                                break;
                            }
                            System.out.print("Stock cant be negative\nRe-enter stock to add: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage() + "\nRe-enter stock to add: ");
                        }
                    }
                    items.put(id, new CartItem(name, items.containsKey(id) ? items.get(id).getStock() + stock : stock));
                }
                case 2 -> {
                    if (!items.isEmpty()) {
                        System.out.print("Enter product ID: ");
                        String id;
                        while (true) {
                            try {
                                id = br.readLine();
                                if (items.containsKey(id)) {
                                    break;
                                }
                                System.out.print("Product ID doesn't exist\nRe-enter product ID: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage() + "\nRe-enter product ID: ");
                            }
                        }

                        System.out.print("Enter item stock to remove: ");
                        int stock;
                        while (true) {
                            try {
                                stock = Integer.parseInt(br.readLine());
                                if (stock >= 0 && items.get(id).getStock() - stock >= 0) {
                                    break;
                                }
                                System.out.print("Stock cant be negative\nRe-enter stock to remove: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage() + "\nRe-enter stock to remove: ");
                            }
                        }

                        CartItem item = items.get(id);
                        int newStock = item.getStock() - stock;
                        if (newStock == 0) {
                            items.remove(id);
                            System.out.println("Item '" + item.getItem() + "' removed from cart.");
                        } else {
                            items.put(id, new CartItem(item.getItem(), newStock));
                        }
                    } else {
                        System.out.println("No item yet");
                    }
                }
                case 3 -> {
                    if (!items.isEmpty()) {
                        System.out.print("Enter price per item: ");
                        double price;
                        while (true) {
                            try {
                                price = Double.parseDouble(br.readLine());
                                if (price > 0) {
                                    break;
                                }
                                System.out.print("Price must be positive\nRe-enter price per item: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage() + "\nRe-enter price per item: ");
                            }
                        }

                        System.out.println(
                                "Total cost: " + items.values().stream().mapToInt(p -> p.getStock()).sum() * price);
                    } else {
                        System.out.println("No item yet");
                    }
                }
                case 4 -> {
                    if (!items.isEmpty()) {
                        items.entrySet().stream().sorted(Comparator.comparing(e -> e.getValue().getItem()))
                                .forEach(e -> System.out.println("Item ID: " + e.getKey() + "\nItem name: "
                                        + e.getValue().getItem() + "\nItem stock: " + e.getValue().getStock() + "\n"));
                    } else {
                        System.out.println("No item yet");
                    }
                }
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter choice: ");
            }
        }
    }
}