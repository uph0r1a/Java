import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import module.Phone;

public class Main {
    public static boolean validateID(List<Phone> phone, String ID) {
        for (int i = 0; i < phone.size(); i++) {
            if (phone.get(i).getPhoneID().equals(ID)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<Phone> phone = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("1)Add phone\n2)Phone list\n3)Update price\n4)Delete phone\n5)Exit\nEnter your choice: ");
            int choice = Integer.parseInt(br.readLine());

            switch (choice) {
                case 1:
                    String phoneID, phoneName, brand;
                    double price;
                    int stock;

                    System.out.print("Enter phone ID: ");
                    while (true) {
                        phoneID = br.readLine();
                        if (!phoneID.isEmpty() && !validateID(phone, phoneID)) {
                            break;
                        }
                        System.out.print("Phone ID cannot be empty\nRe-enter phone ID: ");
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
                        price = Double.parseDouble(br.readLine());
                        if (price > 0) {
                            break;
                        }
                        System.out.print("Price cannot be negative\nRe-enter price: ");
                    }

                    System.out.print("Enter stock: ");
                    while (true) {
                        stock = Integer.parseInt(br.readLine());
                        if (stock > 0) {
                            break;
                        }
                        System.out.print("Stock cannot be negative\nRe-enter stock: ");
                    }

                    phone.add(new Phone(phoneID, phoneName, brand, price, stock));
                    break;
                case 2:
                    if (phone.size() == 0) {
                        System.out.println("No phone exist");
                    } else {
                        for (int i = 0; i < phone.size(); i++) {
                            System.out.println("Phone " + (i + 1) + "\nPhone ID: " + phone.get(i).getPhoneID()
                                    + "\nPhone name: " + phone.get(i).getPhoneName() + "\nBrand: "
                                    + phone.get(i).getBrand()
                                    + "\nPrice: " + phone.get(i).getPrice() + "\nStock: " + phone.get(i).getStock());
                        }
                    }
                    break;
                case 3:
                    boolean found = false;
                    if (phone.size() == 0) {
                        System.out.println("No phone exist");
                    } else {
                        System.out.print("Enter phone ID: ");
                        while (true) {
                            phoneID = br.readLine();
                            if (!phoneID.isEmpty() && validateID(phone, phoneID)) {
                                break;
                            }
                            System.out.print("Phone ID cannot be empty\nRe-enter phone ID: ");
                        }

                        System.out.print("Enter price: ");
                        while (true) {
                            price = Double.parseDouble(br.readLine());
                            if (price > 0) {
                                break;
                            }
                            System.out.print("Price cannot be negative\nRe-enter price: ");
                        }

                        for (int i = 0; i < phone.size(); i++) {
                            if (phone.get(i).getPhoneID().equals(phoneID)) {
                                phone.get(i).setPrice(price);
                                found = true;
                            }
                        }
                        if (!found) {
                            System.out.println("Phone not found");
                        }
                    }
                    break;
                case 4:
                    if (phone.size() == 0) {
                        System.out.println("No phone exist");
                    } else {
                        found = false;
                        System.out.print("Enter phone ID: ");
                        while (true) {
                            phoneID = br.readLine();
                            if (!phoneID.isEmpty() && validateID(phone, phoneID)) {
                                break;
                            }
                            System.out.print("Phone ID cannot be empty\nRe-enter phone ID: ");
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
                    break;
                case 5:
                    isExit = true;
                    break;
                default:
                    System.out.println("Invalid choice");
                    break;
            }
        }
    }
}