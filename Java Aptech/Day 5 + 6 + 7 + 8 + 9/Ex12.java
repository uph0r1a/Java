import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class Ex12 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static abstract class Doctor {
        protected String doctorID, doctorName, specialization;
        protected float salary;

        public Doctor() {
            this.doctorID = UUID.randomUUID().toString();
        }

        public Doctor(String doctorID, String doctorName, String specialization, float salary) {
            this.doctorID = doctorID;
            this.doctorName = doctorName;
            this.specialization = specialization;
            this.salary = salary;
        }

        public String getDoctorID() {
            return doctorID;
        }

        public void setDoctorID(String doctorID) {
            this.doctorID = doctorID;
        }

        public String getDoctorName() {
            return doctorName;
        }

        public void setDoctorName(String doctorName) {
            this.doctorName = doctorName;
        }

        public String getSpecialization() {
            return specialization;
        }

        public void setSpecialization(String specialization) {
            this.specialization = specialization;
        }

        public float getSalary() {
            return salary;
        }

        public void setSalary(float salary) {
            this.salary = salary;
        }

        public abstract void input(List<String> existingIds) throws IOException;

        public abstract void display();
    }

    public static class SpecialistDoctor extends Doctor {
        private String expertise;
        private int yearsOfExperience;

        public SpecialistDoctor() {
        }

        public SpecialistDoctor(String doctorID, String doctorName, String specialization, float salary,
                String expertise, int yearsOfExperience) {
            super(doctorID, doctorName, specialization, salary);
            this.expertise = expertise;
            this.yearsOfExperience = yearsOfExperience;
        }

        public String getExpertise() {
            return expertise;
        }

        public void setExpertise(String expertise) {
            this.expertise = expertise;
        }

        public int getYearsOfExperience() {
            return yearsOfExperience;
        }

        public void setYearsOfExperience(int yearsOfExperience) {
            this.yearsOfExperience = yearsOfExperience;
        }

        @Override
        public void input(List<String> existingIds) throws IOException {
            System.out.print("Enter doctor ID: ");
            String id;
            while (true) {
                id = br.readLine().strip();
                if (!existingIds.contains(id)) {
                    break;
                }
                System.out.print("ID already exists\nRe-enter doctor ID: ");
            }

            System.out.print("Enter doctor name: ");
            String name = br.readLine().strip();

            System.out.print("Enter doctor specialization: ");
            String specialization = br.readLine().strip();

            System.out.print("Enter doctor salary: ");
            float salary;
            while (true) {
                try {
                    salary = Float.parseFloat(br.readLine());
                    if (salary >= 0) {
                        break;
                    }
                    System.out.print("Invalid salary\nRe-enter doctor salary: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter doctor expertise: ");
            String expertise = br.readLine().strip();

            System.out.print("Enter years of experience: ");
            int year;
            while (true) {
                try {
                    year = Integer.parseInt(br.readLine());
                    if (year >= 0) {
                        break;
                    }
                    System.out.print("Invalid years of experience\nRe-enter years of experience: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            setDoctorID(id);
            setDoctorName(name);
            setSpecialization(specialization);
            setSalary(salary);
            setExpertise(expertise);
            setYearsOfExperience(year);
        }

        @Override
        public void display() {
            System.out.println("\nID: " + getDoctorID() + "\nDoctor name: " + getDoctorName() + "\nSpecialization: "
                    + getSpecialization() + "\nSalary: " + getSalary() + "\nExpertise: " + getExpertise()
                    + "\nYears of experience: " + getYearsOfExperience());
        }
    }

    public static class Surgeon extends Doctor {
        private int numberOfSurgeries;
        private String hospitalName;

        public Surgeon() {
        }

        public Surgeon(String doctorID, String doctorName, String specialization, float salary,
                int numberOfSurgeries, String hospitalName) {
            super(doctorID, doctorName, specialization, salary);
            this.numberOfSurgeries = numberOfSurgeries;
            this.hospitalName = hospitalName;
        }

        public int getNumberOfSurgeries() {
            return numberOfSurgeries;
        }

        public void setNumberOfSurgeries(int numberOfSurgeries) {
            this.numberOfSurgeries = numberOfSurgeries;
        }

        public String getHospitalName() {
            return hospitalName;
        }

        public void setHospitalName(String hospitalName) {
            this.hospitalName = hospitalName;
        }

        @Override
        public void input(List<String> existingIds) throws IOException {
            System.out.print("Enter doctor ID: ");
            String id;
            while (true) {
                id = br.readLine().strip();
                if (!existingIds.contains(id)) {
                    break;
                }
                System.out.print("ID already exists\nRe-enter doctor ID: ");
            }

            System.out.print("Enter doctor name: ");
            String dName = br.readLine().strip();

            System.out.print("Enter doctor specialization: ");
            String specialization = br.readLine().strip();

            System.out.print("Enter doctor salary: ");
            float salary;
            while (true) {
                try {
                    salary = Float.parseFloat(br.readLine());
                    if (salary >= 0) {
                        break;
                    }
                    System.out.print("Invalid salary\nRe-enter doctor salary: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter number of surgeries: ");
            int surgeries;
            while (true) {
                try {
                    surgeries = Integer.parseInt(br.readLine());
                    if (surgeries >= 0) {
                        break;
                    }
                    System.out.print("Invalid number of surgeries\nRe-enter number of surgeries: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter hospital name: ");
            String hName = br.readLine().strip();

            setDoctorID(id);
            setDoctorName(dName);
            setSpecialization(specialization);
            setSalary(salary);
            setNumberOfSurgeries(surgeries);
            setHospitalName(hName);
        }

        @Override
        public void display() {
            System.out.println("\nID: " + getDoctorID() + "\nDoctor name: " + getDoctorName() + "\nSpecialization: "
                    + getSpecialization() + "\nSalary: " + getSalary() + "\nNumber of surgeries: "
                    + getNumberOfSurgeries() + "\nHospital name: " + getHospitalName());
        }
    }

    public static void main(String[] args) throws IOException {
        List<SpecialistDoctor> specialists = new ArrayList<>();
        List<Surgeon> surgeons = new ArrayList<>();
        boolean isExit = false;

        while (!isExit) {
            System.out.print("""
                    ===== HOSPITAL DOCTOR MANAGEMENT =====
                    1. Enter information for n specialist doctors.
                    2. Enter information for n surgeons.
                    3. Display information for n specialist doctors (sorted by salary, descending).
                    4. Display information for n surgeons (sorted by number of surgeries, ascending).
                    5. Exit.
                    Your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
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
                    System.out.print("Enter number of specialist doctors: ");
                    int number;
                    while (true) {
                        try {
                            number = Integer.parseInt(br.readLine());
                            if (number >= 0) {
                                break;
                            }
                            System.out.print(
                                    "Invalid number of specialist doctors\nRe-enter number of specialist doctors: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }
                    for (int i = 0; i < number; i++) {
                        List<String> existingIds = new ArrayList<>();
                        specialists.forEach(d -> existingIds.add(d.getDoctorID()));
                        surgeons.forEach(d -> existingIds.add(d.getDoctorID()));

                        SpecialistDoctor sd = new SpecialistDoctor();
                        sd.input(existingIds);
                        specialists.add(sd);
                    }
                }
                case 2 -> {
                    System.out.print("Enter number of surgeons: ");
                    int number;
                    while (true) {
                        try {
                            number = Integer.parseInt(br.readLine());
                            if (number >= 0) {
                                break;
                            }
                            System.out.print("Invalid number of surgeons\nRe-enter number of surgeons: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }
                    for (int i = 0; i < number; i++) {
                        List<String> existingIds = new ArrayList<>();
                        specialists.forEach(d -> existingIds.add(d.getDoctorID()));
                        surgeons.forEach(d -> existingIds.add(d.getDoctorID()));

                        Surgeon s = new Surgeon();
                        s.input(existingIds);
                        surgeons.add(s);
                    }
                }
                case 3 -> {
                    if (specialists.isEmpty()) {
                        System.out.println("No specialist doctor yet");
                    } else {
                        specialists.stream()
                                .sorted(Comparator.comparingDouble(SpecialistDoctor::getSalary).reversed())
                                .forEach(SpecialistDoctor::display);
                    }
                }
                case 4 -> {
                    if (surgeons.isEmpty()) {
                        System.out.println("No surgeon yet");
                    } else {
                        surgeons.stream().sorted(Comparator.comparingInt(Surgeon::getNumberOfSurgeries))
                                .forEach(Surgeon::display);
                    }
                }
                case 5 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}