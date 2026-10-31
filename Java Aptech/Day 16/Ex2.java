import java.util.ArrayList;
import java.util.List;

public class Ex2 {
    static class Person {
        String name;
        int age;
        List<Person> children;

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
            this.children = new ArrayList<>();
        }

        public void addChild(Person child) {
            children.add(child);
        }
    }

    public static int countFamilyMembers(Person node) {
        if (node == null) {
            return 0;
        }

        int count = 1;

        for (Person child : node.children) {
            count += countFamilyMembers(child);
        }

        return count;
    }

    public static int getMaxGeneration(Person node) {
        if (node == null) {
            return 0;
        }

        int maxGeneration = 0;

        for (Person child : node.children) {
            int childGeneration = getMaxGeneration(child);

            if (childGeneration > maxGeneration) {
                maxGeneration = childGeneration;
            }
        }

        return maxGeneration + 1;
    }

    public static void printFamilyTree(Person node, String prefix) {
        if (node == null) {
            return;
        }

        System.out.println(prefix + node.name + " (" + node.age + " years old)");

        for (Person child : node.children) {
            printFamilyTree(child, prefix + "    ");
        }
    }

    public static boolean isAncestor(Person ancestor, Person descendant) {
        if (ancestor == null || descendant == null) {
            return false;
        }

        for (Person child : ancestor.children) {
            if (child == descendant) {
                return true;
            }

            if (isAncestor(child, descendant)) {
                return true;
            }
        }

        return false;
    }

    public static void main(String[] args) {
        Person grandfather = new Person("Grandfather", 75), grandmother = new Person("Grandmother", 72),
                father = new Person("Father", 45), son = new Person("Son", 20), daughter = new Person("Daughter", 17),
                grandson = new Person("Grandson", 2);

        grandfather.addChild(father);
        father.addChild(son);
        father.addChild(daughter);
        son.addChild(grandson);

        System.out.println("===== TOTAL FAMILY MEMBERS =====\n" + countFamilyMembers(grandfather)
                + "\n===== MAXIMUM GENERATION =====\n" + getMaxGeneration(grandfather) + "\n===== FAMILY TREE =====");
        printFamilyTree(grandfather, "");

        System.out.println("\n===== ANCESTOR CHECK =====\nIs Grandfather an ancestor of Grandson? "
                + isAncestor(grandfather, grandson) + "\nIs Father an ancestor of Grandson? "
                + isAncestor(father, grandson) + "\nIs Daughter an ancestor of Grandson? "
                + isAncestor(daughter, grandson) + "\nIs Grandmother an ancestor of Grandson? "
                + isAncestor(grandmother, grandson));
    }
}
