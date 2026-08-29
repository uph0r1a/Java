public class Ex7 {
    public static abstract class PersonAbs {
        private String name;
        private int age;

        public PersonAbs(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public abstract void gotoWork();
    }

    public static class Person extends PersonAbs {
        private int idNumber;

        public Person(int idNumber, String name, int age) {
            super(name, age);
            this.idNumber = idNumber;
        }

        public int getIdNumber() {
            return idNumber;
        }

        public void setIdNumber(int idNumber) {
            this.idNumber = idNumber;
        }

        @Override
        public void gotoWork() {
            System.out.println("Go to work");
        }
    }

    public static interface IHotel {
        public void addCustomer(Person guest, String roomType, int nights);

        public void deleteCustomer(String idNumber);

        public double calculatePrice(String idNumber);
    }

    public static void main(String[] args) {

    }
}
