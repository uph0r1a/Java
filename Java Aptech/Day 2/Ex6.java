import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Ex6 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static class Person {
        private String name;
        private int age;
        private boolean gender;

        public Person(String name, int age, boolean gender) {
            this.name = name;
            this.age = age;
            this.gender = gender;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public boolean isGender() {
            return gender;
        }

        public void setGender(boolean gender) {
            this.gender = gender;
        }

        public void getInfo() {
            System.out.println("Name: " + name + "\nAge: " + age + "\nGender: " + (gender ? "Male" : "Female"));
        }
    }

    public static class Student {
        private String studentID, name;
        private int age;
        private double[] grade;

        public Student() {
            this.studentID = "";
            this.name = "";
            this.age = 0;
            this.grade = null;
        }

        public Student(String studentID, String name) {
            this.studentID = studentID;
            this.name = name;
            this.age = 0;
            this.grade = null;
        }

        public Student(String studentID, String name, int age) {
            this.studentID = studentID;
            this.name = name;
            this.age = age;
            this.grade = null;
        }

        public Student(String studentID, String name, int age, double[] grade) {
            this.studentID = studentID;
            this.name = name;
            this.age = age;
            this.grade = grade;
        }

        public double calculateGPA(double[] grades) {
            return Arrays.stream(grades).average().orElse(0);
        }
    }

    public static class BankAccount {
        private double balance;

        public BankAccount(double balance) {
            this.balance = balance;
        }

        public void deposit(double deposit) {
            balance += deposit;
        }

        public void withdraw(double withdraw) {
            balance -= withdraw;
        }

        public double getBalance() {
            return balance;
        }
    }

    public static class Computer {
        private String brand, model;
        private double price;

        public Computer(String brand, String model, double price) {
            this.brand = brand;
            this.model = model;
            this.price = price;
        }

        public void displayInfo() {
            System.out.println("Brand: " + brand + "\nModel: " + model + "\nPrice: " + price);
        }
    }

    public static class Circle {
        private double radius;

        public Circle(double radius) {
            this.radius = radius;
        }

        public double calculateArea() {
            return Math.PI * Math.pow(radius, 2);
        }

        public double calculatePerimeter() {
            return 2 * Math.PI * radius;
        }
    }

    public static class Animal {
        private String name, sound;

        public Animal(String name, String sound) {
            this.name = name;
            this.sound = sound;
        }

        public void makeSound() {
            System.out.println("Sound: " + sound);
        }
    }

    public static class Product {
        private String name;
        private int price;
        private float quantity;

        public Product(String name, int price, float quantity) {
            this.name = name;
            this.price = price;
            this.quantity = quantity;
        }

        public float calculateTotalPrice() {
            return price * quantity;
        }
    }

    public static class Car {
        private String brand, model;
        private int year;
        private double price;

        public Car(String brand, String model, int year, double price) {
            this.brand = brand;
            this.model = model;
            this.year = year;
            this.price = price;
        }

        public double calculateDepreciation() {
            return price * Math.pow(0.95, (Year.now().getValue() - year));
        }
    }

    public static class Employee {
        private String employeeID, name, position;
        private double salary;

        public Employee(String employeeID, String name, String position, double salary) {
            this.employeeID = employeeID;
            this.name = name;
            this.position = position;
            this.salary = salary;
        }

        public double calculateAnnualSalary() {
            return salary * 12;
        }
    }

    public static class PersonList {
        private List<Person> personList = new ArrayList<>();

        public PersonList(List<Ex6.Person> personList) {
            this.personList = personList;
        }

        public void add() throws NumberFormatException, IOException {
            System.out.print("Enter person name: ");
            String name = br.readLine();

            System.out.print("Enter person age: ");
            int age;
            while (true) {
                try {
                    age = Integer.parseInt(br.readLine());
                    if (age > 0) {
                        break;
                    }
                    System.out.print("Age cant be negative\nRe-enter person age: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            System.out.print("Enter gender 1) Male 2) Female: ");
            int gender;
            while (true) {
                try {
                    gender = Integer.parseInt(br.readLine());
                    if (gender == 1 || gender == 2) {
                        break;
                    }
                    System.out.print("Invalid gender\nRe-enter gender");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            personList.add(new Person(name, age, gender == 1));
        }

        public void remove() throws IOException {
            System.out.print("Enter person name: ");
            String name = br.readLine();

            for (Person person : personList) {
                if (person.getName().toLowerCase().contains(name.toLowerCase())) {
                    personList.remove(person);
                }
            }
        }

        public void search() throws IOException {
            System.out.print("Enter person name: ");
            String name = br.readLine();

            for (Person person : personList) {
                if (person.getName().toLowerCase().contains(name.toLowerCase())) {
                    person.getInfo();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        boolean isExit = false;
        while (!isExit) {
            System.out
                    .print("""
                            Please choose a feature:
                            1. Create a Person object and display its information
                            2. Create 4 Student objects and display each student's average grade (using 4 different constructors)
                            3. Create a bank account and deposit/withdraw
                            4. Create a computer object and display its information
                            5. Create a circle and display its perimeter and area
                            6. Create an animal object and print its sound
                            7. Create a car object and print its depreciation
                            8. Create an employee object and print their annual salary
                            9. Add, remove, and search for a Person in the list
                            0. Exit
                            Enter your choice:\s""");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 0 && choice <= 9) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            switch (choice) {
                case 1 -> {
                    System.out.print("Enter person name: ");
                    String name = br.readLine();

                    System.out.print("Enter person age: ");
                    int age;
                    while (true) {
                        try {
                            age = Integer.parseInt(br.readLine());
                            if (age > 0) {
                                break;
                            }
                            System.out.print("Age cant be negative\nRe-enter person age: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    System.out.print("Enter gender 1) Male 2) Female: ");
                    int gender;
                    while (true) {
                        try {
                            gender = Integer.parseInt(br.readLine());
                            if (gender == 1 || gender == 2) {
                                break;
                            }
                            System.out.print("Invalid gender\nRe-enter gender");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }

                    Person person = new Person(name, age, gender == 1);
                    person.getInfo();
                }
                case 2 -> {

                    System.out.print("Enter student ID: ");
                    String id = br.readLine();

                    System.out.print("Enter student name: ");
                    String name = br.readLine();

                    System.out.print("Enter student age: ");
                    int age;
                    while (true) {
                        try {
                            age = Integer.parseInt(br.readLine());
                            if (age > 0) {
                                break;
                            }
                            System.out.print("Invalid age\nRe-enter age: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }
                    System.out.print("How many grade ? ");
                    int numberOfGrade;
                    while (true) {
                        try {
                            numberOfGrade = Integer.parseInt(br.readLine());
                            if (numberOfGrade > 0) {
                                break;
                            }
                            System.out.print("Number of grades cant be negative\nRe-enter number of grade: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }
                    List<Double> grades = new ArrayList<>();
                    for (int i = 0; i < numberOfGrade; i++) {
                        System.out.print("Enter grade: ");
                        double grade;
                        while (true) {
                            try {
                                grade = Double.parseDouble(br.readLine());
                                if (grade >= 0 && grade <= 10) {
                                    break;
                                }
                                System.out.print("Invalid grade\nRe-enter grade: ");
                            } catch (Exception e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }
                        grades.add(grade);
                    }

                    double[] gradeArray = grades.stream().mapToDouble(Double::doubleValue).toArray();
                    Student student1 = new Student(), student2 = new Student(id, name),
                            student3 = new Student(id, name, age), student4 = new Student(id, name, age, gradeArray);
                    System.out.println("Student 1 grade: " + student1.calculateGPA(null) + "\nStudent 2 grade: "
                            + student2.calculateGPA(null) + "\nStudent 3 grade: " + student3.calculateGPA(null)
                            + "\nStudent 4 grade: " + student4.calculateGPA(gradeArray));
                }
                case 3 -> {
                    System.out.print("Enter balance: ");
                    double balance;
                    while (true) {
                        try {
                            balance = Double.parseDouble(br.readLine());
                            if (balance >= 0) {
                                break;
                            }
                            System.out.print("Invalid balance\nRe-enter balance: ");
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }
                    BankAccount account = new BankAccount(balance);
                    System.out.println("Balance: " + account.getBalance());
                }
                case 4 -> {
                    System.out.println("");
                }
                case 5 -> {
                }
                case 6 -> {
                }
                case 7 -> {
                }
                case 8 -> {
                }
                case 9 -> {
                }
                case 0 -> isExit = true;
                default -> System.out.print("Invalid choice\nRe-enter your choice: ");
            }
        }
    }
}
