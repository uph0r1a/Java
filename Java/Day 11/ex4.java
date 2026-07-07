public class ex4 {
    static class Month {
        private int monthNumber;
        private final String[] monthName = { "January", "February", "March", "April", "May", "June", "July", "August",
                "September", "October", "November", "December" };

        public Month() {
            monthNumber = 1;
        }

        public Month(int monthNumber) throws InvalidMonthNumber {
            if (monthNumber < 1 || monthNumber > 12) {
                throw new InvalidMonthNumber("Invalid month number: " + monthNumber);
            }
            this.monthNumber = monthNumber;
        }

        public Month(String name) throws InvalidMonthName {
            setMonthName(name);
        }

        public int getMonthNumber() {
            return monthNumber;
        }

        public void setMonthNumber(int monthNumber) throws InvalidMonthNumber {
            if (monthNumber < 1 || monthNumber > 12) {
                throw new InvalidMonthNumber("Invalid month number: " + monthNumber);
            }
            this.monthNumber = monthNumber;
        }

        public String getMonthName() {
            return monthName[monthNumber - 1];
        }

        public void setMonthName(String name) throws InvalidMonthName {
            for (int i = 0; i < monthName.length; i++) {
                if (monthName[i].equalsIgnoreCase(name)) {
                    monthNumber = i + 1;
                    return;
                }
            }
            throw new InvalidMonthName("Invalid month name: " + name);
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

    static class InvalidMonthNumber extends Exception {
        public InvalidMonthNumber(String message) {
            super(message);
        }
    }

    static class InvalidMonthName extends Exception {
        public InvalidMonthName(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        try {
            Month m1 = new Month(5);
            System.out.println("Created: " + m1);
        } catch (InvalidMonthNumber e) {
            System.out.println("Error: " + e.getMessage());
        }

        try {
            Month m2 = new Month(13);
            System.out.println("Created: " + m2);
        } catch (InvalidMonthNumber e) {
            System.out.println("Error: " + e.getMessage());
        }

        try {
            Month m3 = new Month("December");
            System.out.println("Created: " + m3);
        } catch (InvalidMonthName e) {
            System.out.println("Error: " + e.getMessage());
        }

        try {
            Month m4 = new Month("March");
            System.out.println("Created: " + m4);
        } catch (InvalidMonthName e) {
            System.out.println("Error: " + e.getMessage());
        }

        try {
            Month m5 = new Month();
            m5.setMonthNumber(0);
        } catch (InvalidMonthNumber e) {
            System.out.println("Error: " + e.getMessage());
        }

        try {
            Month m6 = new Month();
            m6.setMonthName("January");
        } catch (InvalidMonthName e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}