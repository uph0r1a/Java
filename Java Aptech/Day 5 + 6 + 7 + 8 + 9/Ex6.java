import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        protected String id, destination, date;
        protected int numberOfParticipants;

        public Trip() {
        }

        public Trip(String destination, String date, int numberOfParticipants) {
            this.id = UUID.randomUUID().toString();
            this.destination = destination;
            this.date = date;
            this.numberOfParticipants = numberOfParticipants;
        }

        public Trip(String id, String destination, String date, int numberOfParticipants) {
            this.id = id;
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

        protected void enterCommonInfo() throws IOException {
            System.out.print("Enter destination: ");
            String destinationInput = br.readLine().strip();

            System.out.print("Enter date: ");
            String dateInput;
            while (true) {
                try {
                    dateInput = br.readLine().strip();
                    if (isValidDate(dateInput, pattern)) {
                        break;
                    }
                    System.out.print("Invalid date\nRe-enter date: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter number of participants: ");
            int numberInput;
            while (true) {
                try {
                    numberInput = Integer.parseInt(br.readLine().strip());
                    if (numberInput >= 0) {
                        break;
                    }
                    System.out.print("Invalid number of participants\nRe-enter number of participants: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            setDestination(destinationInput);
            setDate(dateInput);
            setNumberOfParticipants(numberInput);
        }
    }

    public static class FamilyTrip extends Trip {
        private boolean hasChildren, isExtendedFamily;

        public FamilyTrip() {
        }

        public FamilyTrip(String destination, String date, int numberOfParticipants, boolean hasChildren,
                boolean isExtendedFamily) {
            super(destination, date, numberOfParticipants);
            this.hasChildren = hasChildren;
            this.isExtendedFamily = isExtendedFamily;
        }

        public FamilyTrip(String id, String destination, String date, int numberOfParticipants, boolean hasChildren,
                boolean isExtendedFamily) {
            super(id, destination, date, numberOfParticipants);
            this.hasChildren = hasChildren;
            this.isExtendedFamily = isExtendedFamily;
        }

        public boolean isHasChildren() {
            return hasChildren;
        }

        public void setHasChildren(boolean hasChildren) {
            this.hasChildren = hasChildren;
        }

        public boolean isExtendedFamily() {
            return isExtendedFamily;
        }

        public void setExtendedFamily(boolean isExtendedFamily) {
            this.isExtendedFamily = isExtendedFamily;
        }

        @Override
        public void enterInfo() throws IOException {
            enterCommonInfo();

            int childChoice;
            while (true) {
                System.out.print("Is there any children 1) Yes 2) No: ");
                try {
                    childChoice = Integer.parseInt(br.readLine().strip());
                    if (childChoice == 1 || childChoice == 2) {
                        break;
                    }
                    System.out.println("Invalid choice");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            int extFamilyChoice;
            while (true) {
                System.out.print("Is this an extended family 1) Yes 2) No: ");
                try {
                    extFamilyChoice = Integer.parseInt(br.readLine().strip());
                    if (extFamilyChoice == 1 || extFamilyChoice == 2) {
                        break;
                    }
                    System.out.println("Invalid choice");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            setHasChildren(childChoice == 1);
            setExtendedFamily(extFamilyChoice == 1);
            System.out.println("Family trip added successfully.");
        }

        @Override
        public void showInfo() {
            System.out.println("\nTrip ID: " + getId() + "\nTrip destination: " + getDestination() + "\nTrip date: "
                    + getDate() + "\nNumber of participant: " + getNumberOfParticipants() + "\nHas children: "
                    + (isHasChildren() ? "Yes" : "No") + "\nIs extended family: "
                    + (isExtendedFamily() ? "Yes" : "No"));
        }
    }

    public static class AdventureTrip extends Trip {
        private String activityType;
        private int difficultyLevel;

        private enum ActivityType {
            HIKING("Hiking"),
            RAFTING("Rafting"),
            MOUNTAIN_CLIMBING("Mountain Climbing");

            private final String displayName;

            ActivityType(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return this.displayName;
            }

            public static ActivityType fromString(String input) {
                if (input == null)
                    return null;

                String normalizedInput = input.strip();

                for (ActivityType activity : ActivityType.values()) {
                    if (activity.name().equalsIgnoreCase(normalizedInput.replace(" ", "_"))
                            || activity.displayName.equalsIgnoreCase(normalizedInput)) {
                        return activity;
                    }
                }
                return null;
            }
        }

        public AdventureTrip() {
        }

        public AdventureTrip(String destination, String date, int numberOfParticipants, String activityType,
                int difficultyLevel) {
            super(destination, date, numberOfParticipants);
            this.activityType = activityType;
            this.difficultyLevel = difficultyLevel;
        }

        public AdventureTrip(String id, String destination, String date, int numberOfParticipants, String activityType,
                int difficultyLevel) {
            super(id, destination, date, numberOfParticipants);
            this.activityType = activityType;
            this.difficultyLevel = difficultyLevel;
        }

        public String getActivityType() {
            return activityType;
        }

        public void setActivityType(String activityType) {
            this.activityType = activityType;
        }

        public int getDifficultyLevel() {
            return difficultyLevel;
        }

        public void setDifficultyLevel(int difficultyLevel) {
            this.difficultyLevel = difficultyLevel;
        }

        @Override
        public void enterInfo() throws IOException {
            enterCommonInfo();

            System.out.print("Enter activity type: ");
            String activityTypeInput;
            while (true) {
                try {
                    activityTypeInput = br.readLine().strip();
                    if (ActivityType.fromString(activityTypeInput) != null) {
                        break;
                    }
                    System.out.print("Invalid type\nRe-enter activity type: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter difficulty level: ");
            int levelInput;
            while (true) {
                try {
                    levelInput = Integer.parseInt(br.readLine().strip());
                    if (levelInput >= 0) {
                        break;
                    }
                    System.out.print("Invalid difficulty level\nRe-enter difficulty level: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            setActivityType(activityTypeInput);
            setDifficultyLevel(levelInput);
            System.out.println("Adventure trip added successfully.");
        }

        @Override
        public void showInfo() {
            System.out.println("\nTrip ID: " + getId() + "\nTrip destination: " + getDestination() + "\nTrip date: "
                    + getDate() + "\nNumber of participant: " + getNumberOfParticipants() + "\nActivity type: "
                    + getActivityType() + "\nDifficulty level: " + getDifficultyLevel());
        }

        public double estimateDuration() {
            ActivityType type = ActivityType.fromString(getActivityType());
            if (type == null) {
                return 0;
            }
            return switch (type) {
                case HIKING -> difficultyLevel;
                case RAFTING -> difficultyLevel * 1.5;
                case MOUNTAIN_CLIMBING -> difficultyLevel * 2;
            };
        }
    }

    public static void main(String[] args) throws IOException {
        List<Trip> trips = new ArrayList<>();
        boolean isExit = false;
        while (!isExit) {
            System.out.print("""
                    Please select:
                    1. Enter information for n family trips.
                    2. Enter information for n adventure trips.
                    3. Display information for n family trips (sorted by number of participants, descending).
                    4. Display information for n adventure trips (sorted by difficulty level, ascending).
                    5. Exit.
                    Your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine().strip());
                    if (choice >= 1 && choice <= 5) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            switch (choice) {
                case 1 -> {
                    System.out.print("Enter number of family trips: ");
                    int numberFamilyTrip;
                    while (true) {
                        try {
                            numberFamilyTrip = Integer.parseInt(br.readLine().strip());
                            if (numberFamilyTrip >= 0) {
                                break;
                            }
                            System.out.print("Invalid number of family trips\nRe-enter number of family trips: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    for (int i = 0; i < numberFamilyTrip; i++) {
                        FamilyTrip familyTrip = new FamilyTrip();
                        familyTrip.enterInfo();
                        trips.add(familyTrip);
                    }
                }
                case 2 -> {
                    System.out.print("Enter number of adventure trips: ");
                    int numberAdventureTrip;
                    while (true) {
                        try {
                            numberAdventureTrip = Integer.parseInt(br.readLine().strip());
                            if (numberAdventureTrip >= 0) {
                                break;
                            }
                            System.out.print("Invalid number of adventure trips\nRe-enter number of adventure trips: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    for (int i = 0; i < numberAdventureTrip; i++) {
                        AdventureTrip adventureTrip = new AdventureTrip();
                        adventureTrip.enterInfo();
                        trips.add(adventureTrip);
                    }
                }
                case 3 -> {
                    trips.stream().filter(trip -> trip instanceof FamilyTrip).map(trip -> (FamilyTrip) trip)
                            .sorted(Comparator.comparingInt(FamilyTrip::getNumberOfParticipants).reversed())
                            .forEach(FamilyTrip::showInfo);
                }
                case 4 -> {
                    trips.stream().filter(trip -> trip instanceof AdventureTrip).map(trip -> (AdventureTrip) trip)
                            .sorted(Comparator.comparingInt(AdventureTrip::getDifficultyLevel))
                            .forEach(adventureTrip -> {
                                adventureTrip.showInfo();
                                System.out.println(
                                        "Estimated Trip Duration: " + adventureTrip.estimateDuration() + " days");
                            });
                }
                case 5 -> isExit = true;
                default -> System.out.println("Invalid choice");
            }
        }
    }
}