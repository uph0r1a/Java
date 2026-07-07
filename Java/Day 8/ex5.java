public class ex5 {
    static class Month {
        private int monthNumber;
        private final String[] monthName = { "January", "February", "March", "April", "May", "June", "July", "August",
                "September", "October", "November", "December" };

        public Month() {
            monthNumber = 1;
        }

        public Month(int monthNumber) {
            if (monthNumber < 1 || monthNumber > 12) {
                monthNumber = 1;
            }
            this.monthNumber = monthNumber;
        }

        public int getMonthNumber() {
            return monthNumber;
        }

        public void setMonthNumber(int monthNumber) {
            if (monthNumber < 1 || monthNumber > 12) {
                monthNumber = 1;
            }
            this.monthNumber = monthNumber;
        }

        public String getMonthName() {
            return monthName[monthNumber - 1];
        }

        public String toString() {
            return "Month: " + getMonthName();
        }

        public boolean equals(Month other) {
            return monthNumber == other.monthNumber;
        }

        public boolean greaterThan(Month other) {
            return monthNumber > other.monthNumber;
        }

        public boolean lessThan(Month other) {
            return monthNumber < other.monthNumber;
        }
    }

    public static void main(String[] args) {
        Month m1 = new Month(5);
        System.out.println(m1);
    }
}
