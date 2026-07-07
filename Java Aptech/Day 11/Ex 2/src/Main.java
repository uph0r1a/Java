import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;

public class Main {
    static class AirLine {
        private String flightID, origin, destination;
        private int amount;
        private LocalDate startDate;

        public AirLine() {
        }

        public AirLine(String flightID, String origin, String destination, int amount, LocalDate startDate) {
            this.flightID = flightID;
            this.origin = origin;
            this.destination = destination;
            this.amount = amount;
            this.startDate = startDate;
        }

        public String getFlightID() {
            return flightID;
        }

        public void setFlightID(String flightID) {
            this.flightID = flightID;
        }

        public String getOrigin() {
            return origin;
        }

        public void setOrigin(String origin) {
            this.origin = origin;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDate startDate) {
            this.startDate = startDate;
        }

        public void NhapTT() throws NumberFormatException, IOException {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Enter flight ID: ");
            setFlightID(br.readLine());

            System.out.print("Enter origin: ");
            setOrigin(br.readLine());

            System.out.print("Enter destination: ");
            setDestination(br.readLine());

            System.out.print("Enter passenger amount: ");
            setAmount(Integer.parseInt(br.readLine()));

            System.out.print("Enter start date(yyyy-MM-dd): ");
            setStartDate(LocalDate.parse(br.readLine()));
        }

        public void XuatTT() {
            System.out.println("FlightID: " + flightID + "\nOrigin: " + origin + "\nDestination: " + destination
                    + "\nPassenger amount: " + amount + "\nStart date: " + startDate);
        }

    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        AirLine airLine = new AirLine();
        airLine.NhapTT();
        airLine.XuatTT();
    }
}