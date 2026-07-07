public class ex7_8 {
    static class Person {
        private String name, address, telephoneNumber;

        public Person(String name, String address, String telephoneNumber) {
            this.name = name;
            this.address = address;
            this.telephoneNumber = telephoneNumber;
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

        public String getTelephoneNumber() {
            return telephoneNumber;
        }

        public void setTelephoneNumber(String telephoneNumber) {
            this.telephoneNumber = telephoneNumber;
        }
    }

    static class Customer extends Person {
        private String number;
        private boolean isMailingList;

        public Customer(String name, String address, String telephoneNumber, String number, boolean isMailingList) {
            super(name, address, telephoneNumber);
            this.number = number;
            this.isMailingList = isMailingList;
        }

        public String getNumber() {
            return number;
        }

        public void setNumber(String number) {
            this.number = number;
        }

        public boolean isMailingList() {
            return isMailingList;
        }

        public void setMailingList(boolean isMailingList) {
            this.isMailingList = isMailingList;
        }
    }

    static class PreferredCustomer extends Customer {
        private double purchased, discount;

        public PreferredCustomer(String name, String address, String telephoneNumber, String number,
                boolean isMailingList, double purchased) {
            super(name, address, telephoneNumber, number, isMailingList);
            this.purchased = purchased;
            if (purchased >= 2000) {
                discount = 10;
            } else if (purchased >= 1500) {
                discount = 7;
            } else if (purchased >= 1000) {
                discount = 6;
            } else if (purchased >= 500) {
                discount = 5;
            }
        }

        public double getPurchased() {
            return purchased;
        }

        public void setPurchased(double purchased) {
            this.purchased = purchased;
        }

        public double getDiscount() {
            return discount;
        }
    }

    public static void main(String[] args) {
        PreferredCustomer customer = new PreferredCustomer("John", "America", "XXX-XXX-XXXX", "XXX-XXX", true, 3000);

        System.out.println("Customer name: " + customer.getName() + "\nCustomer address: " + customer.getAddress()
                + "\nTelephone number: " + customer.getTelephoneNumber() + "\nCustomer number: " + customer.getNumber()
                + "\nMailing list: " + (customer.isMailingList() ? "Yes" : "No") + "\nCustomer purchased: "
                + customer.getPurchased() + "\nDiscount level: " + customer.getDiscount() + "%");
    }
}
