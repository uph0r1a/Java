import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Ex7 {

    public enum Profession {
        SELF_EMPLOYED("Self-Employed"),
        PROFESSIONAL("Professional"),
        RETIRED("Retired");

        private final String label;

        Profession(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Gender {
        M,
        F
    }

    public static class Customer {
        private int age;
        private int personalAssets;
        private Gender gender;
        private Profession profession;

        public Customer(int age, int personalAssets, Gender gender, Profession profession) {
            this.age = age;
            this.personalAssets = personalAssets;
            this.gender = gender;
            this.profession = profession;
        }

        public int getLoanAmountEligible() {

            if (age >= 16 && age <= 25
                    && (profession == Profession.SELF_EMPLOYED || profession == Profession.PROFESSIONAL)
                    && personalAssets > 25000) {

                if (profession == Profession.PROFESSIONAL) {
                    return 15000;
                }
                return 10000;

            } else if (age >= 26 && age <= 40
                    && (profession == Profession.SELF_EMPLOYED || profession == Profession.PROFESSIONAL)
                    && personalAssets > 40000) {

                if (gender == Gender.F) {
                    return 30000;
                }
                return 25000;

            } else if (age >= 41 && age <= 60
                    && (profession == Profession.SELF_EMPLOYED || profession == Profession.PROFESSIONAL)
                    && personalAssets > 50000) {

                return 40000;

            } else if (age > 60
                    && (profession == Profession.SELF_EMPLOYED || profession == Profession.RETIRED)
                    && personalAssets > 25000) {

                if (profession == Profession.SELF_EMPLOYED) {
                    return 35000 - age * 100;
                } else {
                    return 25000 - age * 100;
                }
            }

            return 0;
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter customer age: ");
        int age = Integer.parseInt(br.readLine());

        System.out.print("Enter customer gender (M/F): ");
        String genderInput = br.readLine().toUpperCase();
        Gender gender = Gender.valueOf(genderInput);

        System.out.println("Select profession:\n1. Self-Employed\n2. Professional\n3. Retired\nEnter choice: ");

        int professionChoice = Integer.parseInt(br.readLine());

        Profession profession;

        switch (professionChoice) {
            case 1:
                profession = Profession.SELF_EMPLOYED;
                break;
            case 2:
                profession = Profession.PROFESSIONAL;
                break;
            case 3:
                profession = Profession.RETIRED;
                break;
            default:
                System.out.println("Invalid profession choice.");
                return;
        }

        System.out.print("Enter personal assets: ");
        int assets = Integer.parseInt(br.readLine());

        Customer customer = new Customer(age, assets, gender, profession);

        int loanAmount = customer.getLoanAmountEligible();

        if (loanAmount > 0) {
            System.out.println("Customer is eligible for a loan\nLoan amount eligible: " + loanAmount);
        } else {
            System.out.println("Customer is not eligible for a loan.");
        }
    }
}