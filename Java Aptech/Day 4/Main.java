import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Main {
    static public class Phone {
        private String phoneID, phoneName, brand;
        private double price;
        private int stock;

        public Phone(String phoneID, String phoneName, String brand, double price, int stock) {
            this.phoneID = phoneID;
            this.phoneName = phoneName;
            this.brand = brand;
            this.price = price;
            this.stock = stock;
        }

        public void setPhoneID(String phoneID) {
            this.phoneID = phoneID;
        }

        public void setPhoneName(String phoneName) {
            this.phoneName = phoneName;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public void setStock(int stock) {
            this.stock = stock;
        }

        public String getPhoneID() {
            return phoneID;
        }

        public String getPhoneName() {
            return phoneName;
        }

        public String getBrand() {
            return brand;
        }

        public double getPrice() {
            return price;
        }

        public int getStock() {
            return stock;
        }
    }

    public static boolean validateID(List<Phone> phone, String ID) {
        for (Phone value : phone) {
            if (value.getPhoneID().equals(ID)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<Phone> phone = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("1)Add phone\n2)Phone list\n3)Update price\n4)Delete phone\n5)Exit\nEnter your choice: ");
            int choice;
            try {
                choice = Integer.parseInt(br.readLine());
            } catch (NumberFormatException e) {
                System.out.println("Invalid choice");
                continue;
            }

            switch (choice) {
                case 1 -> {
                    String phoneID, phoneName, brand;
                    double price;
                    int stock;

                    System.out.print("Enter phone ID: ");
                    while (true) {
                        phoneID = br.readLine();
                        if (phoneID.isEmpty()) {
                            System.out.print("Phone ID cannot be empty\nRe-enter phone ID: ");
                        } else if (validateID(phone, phoneID)) {
                            System.out.print("Phone ID already exists\nRe-enter phone ID: ");
                        } else {
                            break;
                        }
                    }

                    System.out.print("Enter phone name: ");
                    while (true) {
                        phoneName = br.readLine();
                        if (!phoneName.isEmpty()) {
                            break;
                        }
                        System.out.print("Phone name cannot be empty\nRe-enter phone name: ");
                    }

                    System.out.print("Enter brand: ");
                    while (true) {
                        brand = br.readLine();
                        if (!brand.isEmpty()) {
                            break;
                        }
                        System.out.print("Brand cannot be empty\nRe-enter brand: ");
                    }

                    System.out.print("Enter price: ");
                    while (true) {
                        try {
                            price = Double.parseDouble(br.readLine());
                            if (price > 0) {
                                break;
                            }
                            System.out.print("Price cannot be negative\nRe-enter price: ");
                        } catch (NumberFormatException e) {
                            System.out.print("Invalid price\nRe-enter price: ");
                        }
                    }

                    System.out.print("Enter stock: ");
                    while (true) {
                        try {
                            stock = Integer.parseInt(br.readLine());
                            if (stock > 0) {
                                break;
                            }
                            System.out.print("Stock cannot be negative\nRe-enter stock: ");
                        } catch (NumberFormatException e) {
                            System.out.print("Invalid stock\nRe-enter stock: ");
                        }
                    }

                    phone.add(new Phone(phoneID, phoneName, brand, price, stock));
                }
                case 2 -> {
                    if (phone.isEmpty()) {
                        System.out.println("No phone exist");
                    } else {
                        for (int i = 0; i < phone.size(); i++) {
                            System.out.println("Phone " + (i + 1) + "\nPhone ID: " + phone.get(i).getPhoneID()
                                    + "\nPhone name: " + phone.get(i).getPhoneName() + "\nBrand: "
                                    + phone.get(i).getBrand() + "\nPrice: " + phone.get(i).getPrice() + "\nStock: "
                                    + phone.get(i).getStock());
                        }
                    }
                }
                case 3 -> {
                    if (phone.isEmpty()) {
                        System.out.println("No phone exist");
                    } else {
                        boolean found = false;
                        String phoneID;
                        double price;

                        System.out.print("Enter phone ID: ");
                        while (true) {
                            phoneID = br.readLine();
                            if (phoneID.isEmpty()) {
                                System.out.print("Phone ID cannot be empty\nRe-enter phone ID: ");
                            } else if (!validateID(phone, phoneID)) {
                                System.out.print("Phone not found\nRe-enter phone ID: ");
                            } else {
                                break;
                            }
                        }

                        System.out.print("Enter price: ");
                        while (true) {
                            try {
                                price = Double.parseDouble(br.readLine());
                                if (price > 0) {
                                    break;
                                }
                                System.out.print("Price cannot be negative\nRe-enter price: ");
                            } catch (NumberFormatException e) {
                                System.out.print("Invalid price\nRe-enter price: ");
                            }
                        }

                        for (Phone value : phone) {
                            if (value.getPhoneID().equals(phoneID)) {
                                value.setPrice(price);
                                found = true;
                            }
                        }
                        if (!found) {
                            System.out.println("Phone not found");
                        }
                    }
                }
                case 4 -> {
                    if (phone.isEmpty()) {
                        System.out.println("No phone exist");
                    } else {
                        boolean found = false;
                        String phoneID;

                        System.out.print("Enter phone ID: ");
                        while (true) {
                            phoneID = br.readLine();
                            if (phoneID.isEmpty()) {
                                System.out.print("Phone ID cannot be empty\nRe-enter phone ID: ");
                            } else if (!validateID(phone, phoneID)) {
                                System.out.print("Phone not found\nRe-enter phone ID: ");
                            } else {
                                break;
                            }
                        }

                        for (int i = 0; i < phone.size(); i++) {
                            if (phone.get(i).getPhoneID().equals(phoneID)) {
                                phone.remove(i);
                                found = true;
                                System.out.println("Phone deleted");
                                break;
                            }
                        }
                        if (!found) {
                            System.out.println("Phone not found");
                        }
                    }
                }
                case 5 -> isExit = true;
                default -> System.out.println("Invalid choice");
            }
        }
    }
}