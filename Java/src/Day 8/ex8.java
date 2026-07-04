public class ex8 {
    static public class ParkedCar {
        private String make, model, color, licenseNumber;
        private int minutesParked;

        public ParkedCar(String make, String model, String color, String licenseNumber, int minutesParked) {
            this.make = make;
            this.model = model;
            this.color = color;
            this.licenseNumber = licenseNumber;
            this.minutesParked = minutesParked;
        }

        public String getMake() {
            return make;
        }

        public String getModel() {
            return model;
        }

        public String getColor() {
            return color;
        }

        public String getLicenseNumber() {
            return licenseNumber;
        }

        public int getMinutesParked() {
            return minutesParked;
        }
    }

    static public class ParkingMeter {
        private int minutesPurchased;

        public ParkingMeter(int minutesPurchased) {
            this.minutesPurchased = minutesPurchased;
        }

        public int getMinutesPurchased() {
            return minutesPurchased;
        }
    }

    static public class ParkingTicket {
        private ParkedCar car;
        private String officerName, badgeNumber;
        private double fine;

        public ParkingTicket(ParkedCar car, String officerName, String badgeNumber, int illegalMinutes) {
            this.car = car;
            this.officerName = officerName;
            this.badgeNumber = badgeNumber;
            this.fine = calculateFine(illegalMinutes);
        }

        private double calculateFine(int illegalMinutes) {
            int hours = (int) Math.ceil(illegalMinutes / 60.0);

            if (hours <= 1) {
                return 25.0;
            } else {
                return 25 + (hours - 1) * 10;
            }
        }

        public void printTicket() {
            System.out.println("====== Parking Ticket ======\nCar Make: " + car.getMake() + "\nModel: " + car.getModel()
                    + "\nColor: " + car.getColor() + "\nLicense: " + car.getLicenseNumber() + "\nFine: $" + fine
                    + "\nOfficer: " + officerName + "\nBadge Number: " + badgeNumber);
        }
    }

    static public class PoliceOfficer {
        private String name, badgeNumber;

        public PoliceOfficer(String name, String badgeNumber) {
            this.name = name;
            this.badgeNumber = badgeNumber;
        }

        public ParkingTicket inspectCar(ParkedCar car, ParkingMeter meter) {
            if (car.getMinutesParked() > meter.getMinutesPurchased()) {
                return new ParkingTicket(car, name, badgeNumber, car.getMinutesParked() - meter.getMinutesPurchased());
            }
            return null;
        }
    }

    public static void main(String[] args) {
        ParkedCar car = new ParkedCar("Toyota", "Corolla", "Blue", "ABC-123", 130);
        ParkingMeter meter = new ParkingMeter(60);
        PoliceOfficer officer = new PoliceOfficer("John Smith", "B1234");
        ParkingTicket ticket = officer.inspectCar(car, meter);

        if (ticket != null) {
            ticket.printTicket();
        } else {
            System.out.println("No parking violation.");
        }
    }
}
