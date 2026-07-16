import java.util.List;

public class Ex2 {
    public static class Person {
        private String name;
        private int age;
        private List<Person> person;
        public Person(String name, int age, List<Ex2.Person> person) {
            this.name = name;
            this.age = age;
            this.person = person;
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
        public List<Person> getPerson() {
            return person;
        }
        public void setPerson(List<Person> person) {
            this.person = person;
        }

        public int countFamilyMembers(Person node){

        }

        public int getMaxGeneration(Person node){

        }

        public void printFamilyTree(Person node, String prefix){

        }

        public boolean isAncestor(Person ancestor, Person descendant){
            
        }
    }
    public static void main(String[] args) {
        
    }
}
