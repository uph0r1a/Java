import java.util.ArrayList;
import java.util.List;

public class ex17 {
    static class PhoneBookEntry {
        private String name, phoneNumber;

        public PhoneBookEntry(String name, String phoneNumber) {
            this.name = name;
            this.phoneNumber = phoneNumber;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

    }

    public static void main(String[] args) {
        List<PhoneBookEntry> phoneBookEntries = new ArrayList<>(List.of(
                new PhoneBookEntry("A", "0"),
                new PhoneBookEntry("B", "1"),
                new PhoneBookEntry("C", "2"),
                new PhoneBookEntry("D", "3"),
                new PhoneBookEntry("E", "4")));

        for (PhoneBookEntry phoneBookEntry : phoneBookEntries) {
            System.out.println(
                    "Name: " + phoneBookEntry.getName() + "\nPhone Number: " + phoneBookEntry.getPhoneNumber());
        }
    }
}
