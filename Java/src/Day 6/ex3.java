import java.util.Arrays;

public class ex3 {
    static class PersonalInformation {
        private String name, address, phoneNumber;
        private int age;

        public PersonalInformation(String name, String address, int age, String phoneNumber) {
            this.name = name;
            this.address = address;
            this.age = age;
            this.phoneNumber = phoneNumber;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        @Override
        public String toString() {
            return "PersonalInformation [name=" + name + ", address=" + address + ", phoneNumber=" + phoneNumber
                    + ", age=" + age + "]";
        }
    }

    public static void main(String[] args) {
        PersonalInformation[] info = {
                new PersonalInformation("A", "a", 1, "1A"),
                new PersonalInformation("B", "b", 2, "2B"),
                new PersonalInformation("C", "c", 3, "3C")
        };

        System.out.println(Arrays.toString(info));
    }
}
