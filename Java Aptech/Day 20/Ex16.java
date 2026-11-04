
import java.io.*;
import java.util.*;

public class Ex16 {

    static class Freelancer implements Serializable {
        private String id;
        private String name;
        private float hourlyRate;
        private int totalHoursWorked;
        private List<String> skills;

        public Freelancer() {
        }

        public Freelancer(String id, String name, float hourlyRate,
                          int totalHoursWorked, List<String> skills) {
            this.id = id;
            this.name = name;
            this.hourlyRate = hourlyRate;
            this.totalHoursWorked = totalHoursWorked;
            this.skills = skills;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public float getHourlyRate() {
            return hourlyRate;
        }

        public void setHourlyRate(float hourlyRate) {
            this.hourlyRate = hourlyRate;
        }

        public int getTotalHoursWorked() {
            return totalHoursWorked;
        }

        public void setTotalHoursWorked(int totalHoursWorked) {
            this.totalHoursWorked = totalHoursWorked;
        }

        public List<String> getSkills() {
            return skills;
        }

        public void setSkills(List<String> skills) {
            this.skills = skills;
        }

        @Override
        public String toString() {
            return "Freelancer{" +
                    "id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", hourlyRate=$" + String.format("%.2f", hourlyRate) +
                    ", totalHoursWorked=" + totalHoursWorked +
                    ", skills=" + skills +
                    '}';
        }
    }

    static class Test {

        private static final BufferedReader br =
            new BufferedReader(new InputStreamReader(System.in));

        private static final String FILE_NAME = "freelancers_data.dat";

        public static void main(String[] args) {
            try {
                List<Freelancer> freelancers = inputFreelancers();

                if (!saveFreelancers(freelancers)) {
                    return;
                }

                List<Freelancer> loadedFreelancers =
                    readFreelancers();

                if (loadedFreelancers == null) {
                    return;
                }

                System.out.println("\n========== FREELANCER LIST ==========");
                displayFreelancers(loadedFreelancers);

                searchBySkill(loadedFreelancers);

            } catch (IOException e) {
                System.out.println(
                    "Error reading keyboard input: " + e.getMessage()
                );
            }
        }

        // Input exactly 5 freelancers
        public static List<Freelancer> inputFreelancers()
                throws IOException {

            List<Freelancer> freelancers = new ArrayList<>();

            System.out.println("Enter information for 5 freelancers:");

            for (int i = 0; i < 5; i++) {
                System.out.println("\nFreelancer " + (i + 1));

                // Input ID
                String id = readInput("ID: ");

                // Validate name
                String name;

                while (true) {
                    name = readInput("Name: ");

                    if (name.length() < 3) {
                        System.out.println(
                            "Invalid name: Name must contain at least 3 characters."
                        );
                    } else {
                        break;
                    }
                }

                // Validate hourly rate
                float hourlyRate;

                while (true) {
                    String input = readInput("Hourly rate: ");

                    try {
                        hourlyRate = Float.parseFloat(input);

                        if (!Float.isFinite(hourlyRate) ||
                            hourlyRate <= 15) {
                            System.out.println(
                                "Invalid hourly rate: Hourly rate must be greater than $15."
                            );
                        } else {
                            break;
                        }

                    } catch (NumberFormatException e) {
                        System.out.println(
                            "Invalid hourly rate: Please enter a valid number."
                        );
                    }
                }

                // Validate total hours worked
                int totalHoursWorked;

                while (true) {
                    String input = readInput("Total hours worked: ");

                    try {
                        totalHoursWorked = Integer.parseInt(input);

                        if (totalHoursWorked < 0) {
                            System.out.println(
                                "Invalid total hours worked: Hours cannot be negative."
                            );
                        } else {
                            break;
                        }

                    } catch (NumberFormatException e) {
                        System.out.println(
                            "Invalid total hours worked: Please enter an integer."
                        );
                    }
                }

                // Input skills
                String skillsInput = readInput(
                    "Skills (separate with commas): "
                );

                List<String> skills = new ArrayList<>();

                for (String skill : skillsInput.split(",")) {
                    skill = skill.trim();

                    if (!skill.isEmpty()) {
                        skills.add(skill);
                    }
                }

                freelancers.add(
                    new Freelancer(
                        id, name, hourlyRate,
                        totalHoursWorked, skills
                    )
                );

                System.out.println("Freelancer added successfully.");
            }

            return freelancers;
        }

        // Read keyboard input
        private static String readInput(String message)
                throws IOException {

            System.out.print(message);

            String input = br.readLine();

            if (input == null) {
                throw new EOFException("No more keyboard input.");
            }

            return input.trim();
        }

        // Serialize freelancer list
        public static boolean saveFreelancers(
                List<Freelancer> freelancers) {

            try (ObjectOutputStream oos =
                     new ObjectOutputStream(
                         new FileOutputStream(FILE_NAME))) {

                oos.writeObject(freelancers);

                System.out.println(
                    "\nFreelancers saved successfully to " + FILE_NAME
                );

                return true;

            } catch (IOException e) {
                System.out.println(
                    "Error saving freelancers: " + e.getMessage()
                );

                return false;
            }
        }

        // Deserialize freelancer list
        @SuppressWarnings("unchecked")
        public static List<Freelancer> readFreelancers() {

            try (ObjectInputStream ois =
                     new ObjectInputStream(
                         new FileInputStream(FILE_NAME))) {

                List<Freelancer> freelancers =
                    (List<Freelancer>) ois.readObject();

                System.out.println("Freelancers loaded successfully.");

                return freelancers;

            } catch (IOException e) {
                System.out.println(
                    "Error reading freelancers: " + e.getMessage()
                );

            } catch (ClassNotFoundException e) {
                System.out.println(
                    "Error: Freelancer class could not be found."
                );
            }

            return null;
        }

        // Display all freelancers
        public static void displayFreelancers(
                List<Freelancer> freelancers) {

            for (Freelancer freelancer : freelancers) {
                System.out.println(freelancer);
            }
        }

        // Search freelancers by skill
        public static void searchBySkill(
                List<Freelancer> freelancers) throws IOException {

            System.out.println("\n========== SEARCH BY SKILL ==========");

            String keyword = readInput("Enter skill to search: ");

            boolean found = false;

            for (Freelancer freelancer : freelancers) {
                for (String skill : freelancer.getSkills()) {

                    if (skill.equalsIgnoreCase(keyword)) {
                        if (!found) {
                            System.out.println("\nSearch results:");
                        }

                        System.out.println(freelancer);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                System.out.println("NOT FOUND");
            }
        }
    }
}
