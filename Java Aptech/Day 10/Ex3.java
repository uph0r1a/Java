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

public class Ex3 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    public static String pattern = "dd/MM/uuuu";

    public static boolean isValidDate(String dateStr, String datePattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(datePattern).withResolverStyle(ResolverStyle.STRICT);
        try {
            LocalDate.parse(dateStr, formatter);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static abstract class MusicEvent {
        private String eventID, eventName, date;
        private int numberOfAttendees;

        public MusicEvent() {
        }

        public MusicEvent(String eventName, String date, int numberOfAttendees) {
            this.eventID = UUID.randomUUID().toString();
            this.eventName = eventName;
            this.date = date;
            this.numberOfAttendees = numberOfAttendees;
        }

        public String getEventID() {
            return eventID;
        }

        public String getEventName() {
            return eventName;
        }

        public void setEventName(String eventName) {
            this.eventName = eventName;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public int getNumberOfAttendees() {
            return numberOfAttendees;
        }

        public void setNumberOfAttendees(int numberOfAttendees) {
            this.numberOfAttendees = numberOfAttendees;
        }

        public abstract void input() throws IOException;

        public abstract void display();
    }

    public static class ConcertEvent extends MusicEvent {
        private String artistName, genre;

        public ConcertEvent() {
        }

        public ConcertEvent(String eventName, String date, int numberOfAttendees, String artistName, String genre) {
            super(eventName, date, numberOfAttendees);
            this.artistName = artistName;
            this.genre = genre;
        }

        public String getArtistName() {
            return artistName;
        }

        public void setArtistName(String artistName) {
            this.artistName = artistName;
        }

        public String getGenre() {
            return genre;
        }

        public void setGenre(String genre) {
            this.genre = genre;
        }

        @Override
        public void input() throws IOException {
            System.out.println("Enter event name: ");
            String eName = br.readLine().strip();

            System.out.println("Enter event date: ");
            String date;
            while (true) {
                try {
                    date = br.readLine().strip();
                    if (isValidDate(date, pattern)) {
                        break;
                    }
                    System.out.println("Invalid date\nRe-enter event date: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.println("Enter number of attendees: ");
            int numberOfAttendees;
            while (true) {
                try {
                    numberOfAttendees = Integer.parseInt(br.readLine());
                    if (numberOfAttendees >= 0) {
                        break;
                    }
                    System.out.println("Invalid number of attendees\nRe-enter number of attendees: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.println("Enter artist name: ");
            String aName = br.readLine().strip();

            System.out.println("Enter genre: ");
            String genre = br.readLine().strip();

            setEventName(eName);
            setDate(date);
            setNumberOfAttendees(numberOfAttendees);
            setArtistName(aName);
            setGenre(genre);
        }

        @Override
        public void display() {
            System.out.println("\n" + getClass().toString() + ":\nEvent ID: " + getEventID() + "\nEvent name: "
                    + getEventName() + "\nEvent date: " + getDate() + "\nNumber of attendees: " + getNumberOfAttendees()
                    + "\nArtist name: " + getArtistName() + "\nGenre: " + getGenre());
        }
    }

    public static class FestivalEvent extends MusicEvent {
        private int numberOfStages, durationDays;

        public FestivalEvent() {
        }

        public FestivalEvent(String eventName, String date, int numberOfAttendees, int numberOfStages,
                int durationDays) {
            super(eventName, date, numberOfAttendees);
            this.numberOfStages = numberOfStages;
            this.durationDays = durationDays;
        }

        public int getNumberOfStages() {
            return numberOfStages;
        }

        public void setNumberOfStages(int numberOfStages) {
            this.numberOfStages = numberOfStages;
        }

        public int getDurationDays() {
            return durationDays;
        }

        public void setDurationDays(int durationDays) {
            this.durationDays = durationDays;
        }

        @Override
        public void input() throws IOException {
            System.out.println("Enter event name: ");
            String eName = br.readLine().strip();

            System.out.println("Enter event date: ");
            String date;
            while (true) {
                try {
                    date = br.readLine().strip();
                    if (isValidDate(date, pattern)) {
                        break;
                    }
                    System.out.println("Invalid date\nRe-enter event date: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.println("Enter number of attendees: ");
            int numberOfAttendees;
            while (true) {
                try {
                    numberOfAttendees = Integer.parseInt(br.readLine());
                    if (numberOfAttendees >= 0) {
                        break;
                    }
                    System.out.println("Invalid number of attendees\nRe-enter number of attendees: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.println("Enter number of stages: ");
            int numberOfStages;
            while (true) {
                try {
                    numberOfStages = Integer.parseInt(br.readLine());
                    if (numberOfStages >= 0) {
                        break;
                    }
                    System.out.println("Invalid number of stages\nRe-enter number of stages: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.println("Enter duration days: ");
            int durationDays;
            while (true) {
                try {
                    durationDays = Integer.parseInt(br.readLine());
                    if (durationDays >= 0) {
                        break;
                    }
                    System.out.println("Invalid duration days\nRe-enter duration days: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            setEventName(eName);
            setDate(date);
            setNumberOfAttendees(numberOfAttendees);
            setNumberOfStages(numberOfStages);
            setDurationDays(durationDays);
        }

        @Override
        public void display() {
            System.out.println("\n" + getClass().toString() + ":\nEvent ID: " + getEventID() + "\nEvent name: "
                    + getEventName() + "\nEvent date: " + getDate() + "\nNumber of attendees: " + getNumberOfAttendees()
                    + "\nNumber of stages: " + getNumberOfStages() + "\nDuration days: " + getDurationDays()
                    + "\nEstimate attendence: " + calculateEstimatedAttendance());
        }

        public int calculateEstimatedAttendance() {
            return numberOfStages * durationDays * 1000;
        }
    }

    public static void main(String[] args) throws IOException {
        List<ConcertEvent> concertEvents = new ArrayList<>();
        List<FestivalEvent> festivalEvents = new ArrayList<>();
        boolean isExit = false;
        while (!isExit) {
            System.out.println("""
                    Please select:
                    1. Input information for n Concert Events.
                    2. Input information for n Festival Events.
                    3. Display information of n Concert Events (Sorted by number of attendees descending).
                    4. Display information of n Festival Events (Sorted by duration days ascending).
                    5. Exit.
                    Your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 1 && choice <= 5) {
                        break;
                    }
                    System.out.println("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (choice) {
                case 1 -> {
                    System.out.println("Enter number of concert event: ");
                    int numberOfConcert;
                    while (true) {
                        try {
                            numberOfConcert = Integer.parseInt(br.readLine());
                            if (numberOfConcert >= 0) {
                                break;
                            }
                            System.out.println("Invalid number of concert event\nRe-enter number of concert event: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    for (int i = 0; i < numberOfConcert; i++) {
                        ConcertEvent concert = new ConcertEvent();
                        concert.input();
                        concertEvents.add(concert);
                    }
                }
                case 2 -> {
                    System.out.println("Enter number of festival event: ");
                    int numberOfFestival;
                    while (true) {
                        try {
                            numberOfFestival = Integer.parseInt(br.readLine());
                            if (numberOfFestival >= 0) {
                                break;
                            }
                            System.out.println("Invalid number of festival event\nRe-enter number of festival event: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    for (int i = 0; i < numberOfFestival; i++) {
                        FestivalEvent festival = new FestivalEvent();
                        festival.input();
                        festivalEvents.add(festival);
                    }
                }
                case 3 -> {
                    if (concertEvents.isEmpty()) {
                        System.out.println("No concert event yet");
                    } else {
                        concertEvents.stream()
                                .sorted(Comparator.comparing(ConcertEvent::getNumberOfAttendees).reversed())
                                .forEach(ConcertEvent::display);
                    }
                }
                case 4 -> {
                    if (festivalEvents.isEmpty()) {
                        System.out.println("No festival event yet");
                    } else {
                        festivalEvents.stream().sorted(Comparator.comparing(FestivalEvent::getDurationDays))
                                .forEach(FestivalEvent::display);
                    }
                }
                case 5 -> isExit = true;
                default -> System.out.println("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}
