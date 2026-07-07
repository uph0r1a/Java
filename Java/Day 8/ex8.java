public class ex8 {
    public record ParkedCar(String make, String model, String color, String licenseNumber, int minutesParked) {
    }

    public record ParkingMeter(int minutesPurchased) {
    }

    static public class ParkingTicket {
        private final ParkedCar car;
        private final String officerName, badgeNumber;
        private final double fine;

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
            System.out.println("====== Parking Ticket ======\nCar Make: " + car.make() + "\nModel: " + car.model()
                    + "\nColor: " + car.color() + "\nLicense: " + car.licenseNumber() + "\nFine: $" + fine
                    + "\nOfficer: " + officerName + "\nBadge Number: " + badgeNumber);
        }
    }

    static public class PoliceOfficer {
        private final String name, badgeNumber;

        public PoliceOfficer(String name, String badgeNumber) {
            this.name = name;
            this.badgeNumber = badgeNumber;
        }

        public ParkingTicket inspectCar(ParkedCar car, ParkingMeter meter) {
            if (car.minutesParked() > meter.minutesPurchased()) {
                return new ParkingTicket(car, name, badgeNumber, car.minutesParked() - meter.minutesPurchased());
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
