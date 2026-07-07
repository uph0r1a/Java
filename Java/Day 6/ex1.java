public class ex1 {
    static class Employee {
        private String name, department, position;
        private int idNumber;

        public Employee(String name, int idNumber, String department, String position) {
            this.name = name;
            this.idNumber = idNumber;
            this.department = department;
            this.position = position;
        }

        public Employee(String name, int idNumber) {
            this.name = name;
            this.idNumber = idNumber;
            this.department = "";
            this.position = "";
        }

        public Employee() {
            this.name = "";
            this.idNumber = 0;
            this.department = "";
            this.position = "";
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public int getIdNumber() {
            return idNumber;
        }

        public void setIdNumber(int idNumber) {
            this.idNumber = idNumber;
        }

        public String display() {
            return "\nName: " + name + "\nID Number: " + idNumber + "\nDepartment: " + department + "\nPosition: "
                    + position;
        }

    }

    public static void main(String[] args) {
        Employee[] employees = {
                new Employee("Susan Meyers", 47899, "Accounting", "Vice President"),
                new Employee("Mark Jones", 39119, "IT", "Programmer"),
                new Employee("Joy Rogers", 81774, "Manufacturing", "Engineer")
        };

        for (int i = 0; i < employees.length; i++) {
            System.out.println("Employee " + (i + 1) + employees[i].display());
        }
    }
}
