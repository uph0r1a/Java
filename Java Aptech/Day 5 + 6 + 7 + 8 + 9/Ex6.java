import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.UUID;

public class Ex6 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    public static String pattern = "uuuu-MM-dd";

    public static boolean isValidDate(String dateStr, String formatPattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatPattern)
                .withResolverStyle(ResolverStyle.STRICT);

        try {
            LocalDate.parse(dateStr, formatter);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static abstract class Trip {
        private String id, destination,date;
        private int numberOfParticipants;

        public Trip() {
        }

        public Trip(String destination, String date, int numberOfParticipants) {
            this.id = UUID.randomUUID().toString();
            this.destination = destination;
            this.date = date;
            this.numberOfParticipants = numberOfParticipants;
        }

        public String getId() {
            return id;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public int getNumberOfParticipants() {
            return numberOfParticipants;
        }

        public void setNumberOfParticipants(int numberOfParticipants) {
            this.numberOfParticipants = numberOfParticipants;
        }

        public abstract void enterInfo() throws IOException;

        public abstract void showInfo();
    }

    public static class FamilyTrip extends Trip {
        private boolean hasChildren, familyType;

        public FamilyTrip() {
        }

        public FamilyTrip(String destination, String date, int numberOfParticipants, boolean hasChildren,
                boolean familyType) {
            super(destination, date, numberOfParticipants);
            this.hasChildren = hasChildren;
            this.familyType = familyType;
        }

        public boolean isHasChildren() {
            return hasChildren;
        }

        public void setHasChildren(boolean hasChildren) {
            this.hasChildren = hasChildren;
        }

        public boolean isFamilyType() {
            return familyType;
        }

        public void setFamilyType(boolean familyType) {
            this.familyType = familyType;
        }

        @Override
        public void enterInfo() throws IOException {
            System.out.println("Enter destination: ");
            String destination = br.readLine();

            System.out.println("Enter date: ");
            String expDate;
            while (true) {
                try {
                    expDate = br.readLine();
                    if (isValidDate(expDate, pattern)) {
                        break;
                    }
                    System.out.println("Invalid date\nRe-enter date: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.println("Enter number of participants: ");
            int number;
            while (true) {
                try {
                    number = Integer.parseInt(br.readLine());
                    if (number >= 0) {
                        break;
                    }
                    System.out.println("Invalid number of participants\nRe-enter number of participants: ");
                } catch (Exception e) {
                    System.out.println("Error: "+ e.getMessage());
                }
            }

            int child;
            while (true) {
                System.out.println("Is there any children 1) Yes 2) No: ");
                try {
                    child = Integer.parseInt(br.readLine());
                    if (child == 1 || child == 2) {
                        break;
                    }
                    System.out.println("Invalid choice\n");
                } catch (Exception e) {
                    System.out.println("Error: "+ e.getMessage());
                }
            }
        }

        @Override
        public void showInfo() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'showInfo'");
        }

    }

    public static void main(String[] args) {

    }
}
