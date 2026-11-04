
import java.io.*;
import java.util.*;
import java.time.Year;

public class Ex7 {

    // Account class
    static class Account implements Serializable {
        private static final long serialVersionUID = 1L;

        private String username;
        private String password;
        private String userType;
        private Integer registerYear;
        private String cookie;
        private Float price;

        // Default constructor
        public Account() {
        }

        // Full-argument constructor
        public Account(String username, String password,
                       String userType, Integer registerYear,
                       String cookie, Float price) {

            this.username = username;
            this.password = password;
            this.userType = userType;
            this.registerYear = registerYear;
            this.cookie = cookie;
            this.price = price;
        }

        // Getters
        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getUserType() {
            return userType;
        }

        public Integer getRegisterYear() {
            return registerYear;
        }

        public String getCookie() {
            return cookie;
        }

        public Float getPrice() {
            return price;
        }

        // Setters
        public void setUsername(String username) {
            this.username = username;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public void setUserType(String userType) {
            this.userType = userType;
        }

        public void setRegisterYear(Integer registerYear) {
            this.registerYear = registerYear;
        }

        public void setCookie(String cookie) {
            this.cookie = cookie;
        }

        public void setPrice(Float price) {
            this.price = price;
        }

        @Override
        public String toString() {
            return "Username: " + username
                    + ", Password: " + password
                    + ", User Type: " + userType
                    + ", Register Year: " + registerYear
                    + ", Cookie: " + cookie
                    + ", Price: " + price;
        }
    }

    // Account management
    static class AccountManager {

        private static final BufferedReader br =
                new BufferedReader(
                        new InputStreamReader(System.in)
                );

        private static final ArrayList<Account> accounts =
                new ArrayList<>();

        // Read non-empty text
        private static String readNonBlank(String prompt)
                throws IOException {

            while (true) {
                System.out.print(prompt);
                String value = br.readLine();

                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }

                System.out.println("This field cannot be blank.");
            }
        }

        // Read a positive integer
        private static int readPositiveInteger(String prompt)
                throws IOException {

            while (true) {
                System.out.print(prompt);

                try {
                    int value = Integer.parseInt(br.readLine().trim());

                    if (value > 0) {
                        return value;
                    }

                    System.out.println(
                            "Please enter a positive whole number."
                    );

                } catch (NumberFormatException e) {
                    System.out.println(
                            "Invalid input. Please enter a whole number."
                    );
                }
            }
        }

        // Read register year within the last 5 years
        private static int readRegisterYear()
                throws IOException {

            int currentYear = Year.now().getValue();
            int minYear = currentYear - 5;

            while (true) {
                System.out.print("Register year (" + minYear
                        + " - " + currentYear + "): ");

                try {
                    int year = Integer.parseInt(br.readLine().trim());

                    if (year >= minYear && year <= currentYear) {
                        return year;
                    }

                    System.out.println(
                            "Register year must be between "
                            + minYear + " and " + currentYear + "."
                    );

                } catch (NumberFormatException e) {
                    System.out.println(
                            "Please enter a valid year."
                    );
                }
            }
        }

        // Read price between 0 and 100000
        private static float readPrice()
                throws IOException {

            while (true) {
                System.out.print("Price: ");

                try {
                    float price = Float.parseFloat(br.readLine().trim());

                    if (Float.isFinite(price)
                            && price >= 0
                            && price <= 100000) {
                        return price;
                    }

                    System.out.println(
                            "Price must be between 0 and 100000."
                    );

                } catch (NumberFormatException e) {
                    System.out.println(
                            "Please enter a valid price."
                    );
                }
            }
        }

        // Option 1: Enter account data
        public static void enterData() throws IOException {

            System.out.println("\n===== ENTER ACCOUNT DATA =====");

            int numberOfAccounts =
                    readPositiveInteger("Number of accounts: ");

            for (int i = 0; i < numberOfAccounts; i++) {

                System.out.println(
                        "\nEnter information for account " + (i + 1)
                );

                String username = readNonBlank("Username: ");
                String password = readNonBlank("Password: ");
                String userType = readNonBlank("User type: ");

                int registerYear = readRegisterYear();

                String cookie = readNonBlank("Cookie: ");

                float price = readPrice();

                Account account = new Account(
                        username,
                        password,
                        userType,
                        registerYear,
                        cookie,
                        price
                );

                accounts.add(account);

                System.out.println("Account added successfully.");
            }
        }

        // Display accounts in table format
        private static void displayTable(List<Account> list) {

            System.out.println();
            System.out.printf(
                    "%-18s %-18s %-15s %-15s %-18s %-12s%n",
                    "Username", "Password", "User Type",
                    "Register Year", "Cookie", "Price"
            );

            System.out.println(
                    "-----------------------------------------------------------------------------------------------"
            );

            for (Account account : list) {
                System.out.printf(
                        "%-18s %-18s %-15s %-15d %-18s %-12.2f%n",
                        account.getUsername(),
                        account.getPassword(),
                        account.getUserType(),
                        account.getRegisterYear(),
                        account.getCookie(),
                        account.getPrice()
                );
            }
        }

        // Option 2: Sort by username ascending
        public static void sortByUsername() {

            System.out.println(
                    "\n===== SORT ACCOUNTS BY USERNAME ====="
            );

            if (accounts.isEmpty()) {
                System.out.println("No accounts available.");
                return;
            }

            ArrayList<Account> sortedAccounts =
                    new ArrayList<>(accounts);

            // Bubble Sort - alphabetical ascending
            for (int i = 0; i < sortedAccounts.size() - 1; i++) {
                for (int j = 0;
                     j < sortedAccounts.size() - 1 - i; j++) {

                    String username1 =
                            sortedAccounts.get(j).getUsername();

                    String username2 =
                            sortedAccounts.get(j + 1).getUsername();

                    if (username1.compareToIgnoreCase(username2) > 0) {

                        Account temp = sortedAccounts.get(j);

                        sortedAccounts.set(
                                j, sortedAccounts.get(j + 1)
                        );

                        sortedAccounts.set(j + 1, temp);
                    }
                }
            }

            displayTable(sortedAccounts);
        }

        // Option 3: Sort by price descending
        public static void sortByPrice() {

            System.out.println(
                    "\n===== SORT ACCOUNTS BY PRICE ====="
            );

            if (accounts.isEmpty()) {
                System.out.println("No accounts available.");
                return;
            }

            ArrayList<Account> sortedAccounts =
                    new ArrayList<>(accounts);

            // Bubble Sort - descending
            for (int i = 0; i < sortedAccounts.size() - 1; i++) {
                for (int j = 0;
                     j < sortedAccounts.size() - 1 - i; j++) {

                    if (sortedAccounts.get(j).getPrice()
                            < sortedAccounts.get(j + 1).getPrice()) {

                        Account temp = sortedAccounts.get(j);

                        sortedAccounts.set(
                                j, sortedAccounts.get(j + 1)
                        );

                        sortedAccounts.set(j + 1, temp);
                    }
                }
            }

            displayTable(sortedAccounts);
        }

        // Option 4: Group accounts by user type
        public static void groupByUserType() {

            System.out.println(
                    "\n===== GROUP ACCOUNTS BY USER TYPE ====="
            );

            if (accounts.isEmpty()) {
                System.out.println("No accounts available.");
                return;
            }

            Map<String, Integer> typeCount = new TreeMap<>(
                    String.CASE_INSENSITIVE_ORDER
            );

            for (Account account : accounts) {

                String type = account.getUserType();

                typeCount.put(
                        type,
                        typeCount.getOrDefault(type, 0) + 1
                );
            }

            for (Map.Entry<String, Integer> entry
                    : typeCount.entrySet()) {

                System.out.println(
                        entry.getKey() + ": "
                        + entry.getValue() + " account(s)"
                );
            }
        }

        // Option 5: Find by user type and minimum price
        public static void findByTypeAndPrice()
                throws IOException {

            System.out.println(
                    "\n===== FIND ACCOUNTS BY TYPE AND PRICE ====="
            );

            if (accounts.isEmpty()) {
                System.out.println("No accounts available.");
                return;
            }

            String userType = readNonBlank("Enter user type: ");

            float minPrice;

            while (true) {
                System.out.print("Enter minimum price: ");

                try {
                    minPrice = Float.parseFloat(br.readLine().trim());

                    if (Float.isFinite(minPrice)) {
                        break;
                    }

                    System.out.println("Please enter a valid price.");

                } catch (NumberFormatException e) {
                    System.out.println("Please enter a valid price.");
                }
            }

            boolean found = false;

            for (Account account : accounts) {

                if (account.getUserType().equalsIgnoreCase(userType)
                        && account.getPrice() > minPrice) {

                    System.out.println(account);
                    found = true;
                }
            }

            if (!found) {
                System.out.println("No matching accounts found.");
            }
        }

        // Option 6: Save accounts to file
        public static void saveToFile() throws IOException {

            System.out.println("\n===== SAVE ACCOUNTS TO FILE =====");

            String fileName = readNonBlank("Enter filename: ");

            try (ObjectOutputStream out = new ObjectOutputStream(
                    new FileOutputStream(fileName))) {

                out.writeObject(accounts);

                System.out.println(
                        "Accounts saved successfully to " + fileName
                );

            } catch (IOException e) {
                System.out.println(
                        "Error saving accounts: " + e.getMessage()
                );
            }
        }

        // Option 7: Open file and display accounts
        @SuppressWarnings("unchecked")
        public static void openFile() throws IOException {

            System.out.println("\n===== OPEN ACCOUNT FILE =====");

            String fileName = readNonBlank("Enter filename: ");

            try (ObjectInputStream in = new ObjectInputStream(
                    new FileInputStream(fileName))) {

                ArrayList<Account> loadedAccounts =
                        (ArrayList<Account>) in.readObject();

                if (loadedAccounts.isEmpty()) {
                    System.out.println("The file contains no accounts.");
                    return;
                }

                displayTable(loadedAccounts);

            } catch (FileNotFoundException e) {
                System.out.println("Error: File does not exist.");

            } catch (ClassNotFoundException e) {
                System.out.println(
                        "Error: Account class could not be found."
                );

            } catch (IOException e) {
                System.out.println(
                        "Error reading account file: " + e.getMessage()
                );
            }
        }

        // Display menu
        public static void displayMenu() {

            System.out.println(
                    "\n========== SOCIAL MEDIA ACCOUNT MANAGEMENT =========="
            );

            System.out.println("1. Enter data");
            System.out.println("2. Sort by username (ascending)");
            System.out.println("3. Sort by price (descending)");
            System.out.println("4. Group by user type");
            System.out.println("5. Find by user type and minimum price");
            System.out.println("6. Save to file");
            System.out.println("7. Open file and display");
            System.out.println("8. Exit");

            System.out.println(
                    "======================================================"
            );
        }

        // Ask whether to continue, quit, or clear the screen
        private static boolean askContinue() throws IOException {

            while (true) {

                System.out.print(
                        "\nContinue (y), quit (n), or clear screen (c)? "
                );

                String answer = br.readLine().trim();

                if (answer.equalsIgnoreCase("y")) {
                    return true;
                }

                if (answer.equalsIgnoreCase("n")) {
                    return false;
                }

                if (answer.equalsIgnoreCase("c")) {
                    clearScreen();
                    return true;
                }

                System.out.println(
                        "Invalid input! Enter y, n, or c."
                );
            }
        }

        // Clear terminal screen
        private static void clearScreen() {

            System.out.print("\033[H\033[2J");
            System.out.flush();
        }

        // Main menu loop
        public static void run() throws IOException {

            while (true) {

                displayMenu();

                int choice;

                try {
                    System.out.print("Enter your choice: ");
                    choice = Integer.parseInt(br.readLine().trim());

                } catch (NumberFormatException e) {
                    System.out.println(
                            "Invalid choice! Please enter a number."
                    );

                    continue;
                }

                if (choice < 1 || choice > 8) {
                    System.out.println(
                            "Invalid choice! Please select 1 to 8."
                    );

                    continue;
                }

                if (choice == 8) {
                    System.out.println("Exiting program...");
                    break;
                }

                // Option 1 is required before options 2-7
                if (choice >= 2 && choice <= 7
                        && accounts.isEmpty()) {

                    System.out.println(
                            "Error: Please enter account data first (Option 1)."
                    );

                } else {

                    switch (choice) {

                        case 1:
                            enterData();
                            break;

                        case 2:
                            sortByUsername();
                            break;

                        case 3:
                            sortByPrice();
                            break;

                        case 4:
                            groupByUserType();
                            break;

                        case 5:
                            findByTypeAndPrice();
                            break;

                        case 6:
                            saveToFile();
                            break;

                        case 7:
                            openFile();
                            break;
                    }
                }

                // Ask after every option except Exit
                if (!askContinue()) {
                    System.out.println("Exiting program...");
                    break;
                }
            }
        }
    }

    // Main method
    public static void main(String[] args) {

        try {
            AccountManager.run();

        } catch (IOException e) {
            System.out.println(
                    "Input error: " + e.getMessage()
            );
        }
    }
}
