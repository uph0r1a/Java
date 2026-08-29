import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Ex4 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static abstract class Candidate {
        private String candidateNumber, fullName, address;
        private int priorityLevel;

        public Candidate(String fullName, String address, int priorityLevel) {
            this.candidateNumber = UUID.randomUUID().toString();
            this.fullName = fullName;
            this.address = address;
            this.priorityLevel = priorityLevel;
        }

        public String getCandidateNumber() {
            return candidateNumber;
        }

        public int getPriorityLevel() {
            return priorityLevel;
        }

        public void setPriorityLevel(int priorityLevel) {
            this.priorityLevel = priorityLevel;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        @Override
        public String toString() {
            return "\nCandidate Number: " + candidateNumber + "\nFull Name: " + fullName + "\nAddress: " + address
                    + "\nPriority Level: " + priorityLevel;
        }

        public abstract double scoreSum();
    }

    public static class GroupACandidate extends Candidate {
        private double mathScore, physicsScore, chemistryScore;

        public GroupACandidate(String fullName, String address, int priorityLevel, double mathScore,
                double physicsScore, double chemistryScore) {
            super(fullName, address, priorityLevel);
            this.mathScore = mathScore;
            this.physicsScore = physicsScore;
            this.chemistryScore = chemistryScore;
        }

        public double getMathScore() {
            return mathScore;
        }

        public void setMathScore(double mathScore) {
            this.mathScore = mathScore;
        }

        public double getPhysicsScore() {
            return physicsScore;
        }

        public void setPhysicsScore(double physicsScore) {
            this.physicsScore = physicsScore;
        }

        public double getChemistryScore() {
            return chemistryScore;
        }

        public void setChemistryScore(double chemistryScore) {
            this.chemistryScore = chemistryScore;
        }

        @Override
        public String toString() {
            return "\nGroup A:" + super.toString() + "\nMath Score: " + mathScore + "\nPhysics Score: " + physicsScore
                    + "\nChemistry Score: " + chemistryScore;
        }

        @Override
        public double scoreSum() {
            return mathScore + physicsScore + chemistryScore;
        }
    }

    public static class GroupBCandidate extends Candidate {
        private double mathScore, chemistryScore, biologyScore;

        public GroupBCandidate(String fullName, String address, int priorityLevel, double mathScore,
                double chemistryScore, double biologyScore) {
            super(fullName, address, priorityLevel);
            this.mathScore = mathScore;
            this.chemistryScore = chemistryScore;
            this.biologyScore = biologyScore;
        }

        public double getMathScore() {
            return mathScore;
        }

        public void setMathScore(double mathScore) {
            this.mathScore = mathScore;
        }

        public double getChemistryScore() {
            return chemistryScore;
        }

        public void setChemistryScore(double chemistryScore) {
            this.chemistryScore = chemistryScore;
        }

        public double getBiologyScore() {
            return biologyScore;
        }

        public void setBiologyScore(double biologyScore) {
            this.biologyScore = biologyScore;
        }

        @Override
        public String toString() {
            return "\nGroup B:" + super.toString() + "\nMath Score: " + mathScore + "\nChemistry Score: "
                    + chemistryScore + "\nBiology Score: " + biologyScore;
        }

        @Override
        public double scoreSum() {
            return mathScore + chemistryScore + biologyScore;
        }
    }

    public static class GroupCCandidate extends Candidate {
        private double literatureScore, historyScore, geographyScore;

        public GroupCCandidate(String fullName, String address, int priorityLevel, double literatureScore,
                double historyScore, double geographyScore) {
            super(fullName, address, priorityLevel);
            this.literatureScore = literatureScore;
            this.historyScore = historyScore;
            this.geographyScore = geographyScore;
        }

        public double getLiteratureScore() {
            return literatureScore;
        }

        public void setLiteratureScore(double literatureScore) {
            this.literatureScore = literatureScore;
        }

        public double getHistoryScore() {
            return historyScore;
        }

        public void setHistoryScore(double historyScore) {
            this.historyScore = historyScore;
        }

        public double getGeographyScore() {
            return geographyScore;
        }

        public void setGeographyScore(double geographyScore) {
            this.geographyScore = geographyScore;
        }

        @Override
        public String toString() {
            return "\nGroup C:" + super.toString() + "\nLiteratureScore: " + literatureScore + "\nHistory Score: "
                    + historyScore + "\nGeography Score: " + geographyScore;
        }

        @Override
        public double scoreSum() {
            return literatureScore + historyScore + geographyScore;
        }
    }

    public static class AdmissionManager {
        private List<Candidate> candidates;

        public AdmissionManager() {
            candidates = new ArrayList<>();
        }

        public void add() throws IOException {
            System.out.print("Enter candidate fullname: ");
            String fullName = br.readLine().strip();

            System.out.print("Enter candidate address: ");
            String address = br.readLine().strip();

            System.out.print("Enter priority level: ");
            int priorityLevel;
            while (true) {
                try {
                    priorityLevel = Integer.parseInt(br.readLine());
                    if (priorityLevel >= 0) {
                        break;
                    }
                    System.out.print("Invalid priority level\nRe-enter priority level: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter candidate group 1) Group A 2) Group B 3) Group C: ");
            int group;
            while (true) {
                try {
                    group = Integer.parseInt(br.readLine());
                    if (group >= 1 && group <= 3) {
                        break;
                    }
                    System.out.print("Invalid group\nRe-enter candidate group 1) Group A 2) Group B 3) Group C: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (group) {
                case 1 -> {
                    System.out.print("Enter math score: ");
                    double mathScore;
                    while (true) {
                        try {
                            mathScore = Double.parseDouble(br.readLine());
                            if (mathScore >= 0 && mathScore <= 100) {
                                break;
                            }
                            System.out.print("Invalid math score\nRe-enter math score: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter physics score: ");
                    double physicsScore;
                    while (true) {
                        try {
                            physicsScore = Double.parseDouble(br.readLine());
                            if (physicsScore >= 0 && physicsScore <= 100) {
                                break;
                            }
                            System.out.print("Invalid physics score\nRe-enter physics score: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter chemistry score: ");
                    double chemistryScore;
                    while (true) {
                        try {
                            chemistryScore = Double.parseDouble(br.readLine());
                            if (chemistryScore >= 0 && chemistryScore <= 100) {
                                break;
                            }
                            System.out.print("Invalid chemistry score\nRe-enter chemistry score: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    candidates.add(
                            new GroupACandidate(fullName, address, priorityLevel, mathScore, physicsScore,
                                    chemistryScore));
                }
                case 2 -> {
                    System.out.print("Enter math score: ");
                    double mathScore;
                    while (true) {
                        try {
                            mathScore = Double.parseDouble(br.readLine());
                            if (mathScore >= 0 && mathScore <= 100) {
                                break;
                            }
                            System.out.print("Invalid math score\nRe-enter math score: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter chemistry score: ");
                    double chemistryScore;
                    while (true) {
                        try {
                            chemistryScore = Double.parseDouble(br.readLine());
                            if (chemistryScore >= 0 && chemistryScore <= 100) {
                                break;
                            }
                            System.out.print("Invalid chemistry score\nRe-enter chemistry score: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter biology score: ");
                    double biologyScore;
                    while (true) {
                        try {
                            biologyScore = Double.parseDouble(br.readLine());
                            if (biologyScore >= 0 && biologyScore <= 100) {
                                break;
                            }
                            System.out.print("Invalid biology score\nRe-enter biology score: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    candidates.add(
                            new GroupBCandidate(fullName, address, priorityLevel, mathScore, chemistryScore,
                                    biologyScore));
                }
                case 3 -> {
                    System.out.print("Enter literature score: ");
                    double literatureScore;
                    while (true) {
                        try {
                            literatureScore = Double.parseDouble(br.readLine());
                            if (literatureScore >= 0 && literatureScore <= 100) {
                                break;
                            }
                            System.out.print("Invalid literature score\nRe-enter literature score: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter history score: ");
                    double historyScore;
                    while (true) {
                        try {
                            historyScore = Double.parseDouble(br.readLine());
                            if (historyScore >= 0 && historyScore <= 100) {
                                break;
                            }
                            System.out.print("Invalid history score\nRe-enter history score: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter geography score: ");
                    double geographyScore;
                    while (true) {
                        try {
                            geographyScore = Double.parseDouble(br.readLine());
                            if (geographyScore >= 0 && geographyScore <= 100) {
                                break;
                            }
                            System.out.print("Invalid geography score\nRe-enter geography score: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    candidates.add(new GroupCCandidate(fullName, address, priorityLevel, literatureScore,
                            historyScore, geographyScore));
                }
                default ->
                    System.out.print("Invalid group\nRe-enter candidate group 1) Group A 2) Group B 3) Group C: ");
            }
        }

        public void display() {
            if (candidates.isEmpty()) {
                System.out.println("No candidate yet");
            } else {
                for (Candidate candidate : candidates) {
                    System.out.println(candidate);
                }
            }
        }

        public void search() throws IOException {
            if (candidates.isEmpty()) {
                System.out.println("No candidate yet");
            } else {
                System.out.print("Enter candidate number: ");
                String number = br.readLine().strip();

                for (Candidate candidate : candidates) {
                    if (candidate.getCandidateNumber().equals(number)) {
                        System.out.println(candidate);
                        return;
                    }
                }
                System.out.println("No candidate found");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        AdmissionManager manager = new AdmissionManager();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    1) Add a new candidate.
                    2) Display a candidate's information along with their exam group.
                    3) Search for a candidate by Candidate Number.
                    0) Exit the program
                    Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 3) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (choice) {
                case 1 -> manager.add();
                case 2 -> manager.display();
                case 3 -> manager.search();
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}