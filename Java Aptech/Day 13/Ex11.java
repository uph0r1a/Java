import java.util.LinkedList;
import java.util.Collections;
import java.util.ListIterator;

public class Ex11 {
    public static class Node {
        int val;
        Node next;

        public Node(int val) {
            this.val = val;
            this.next = null;
        }
    }

    public static void printList(LinkedList<Integer> list) {
        System.out.println("List: " + list);
    }

    public static void addFirst(LinkedList<Integer> list, int value) {
        list.addFirst(value);
    }

    public static void addLast(LinkedList<Integer> list, int value) {
        list.addLast(value);
    }

    public static void removeFirst(LinkedList<Integer> list) {
        if (list.isEmpty()) {
            System.out.println("List is empty.");
            return;
        }

        System.out.println("Removed first: " + list.removeFirst());
    }

    public static void removeLast(LinkedList<Integer> list) {
        if (list.isEmpty()) {
            System.out.println("List is empty.");
            return;
        }

        System.out.println("Removed last: " + list.removeLast());
    }

    public static void insertAt(LinkedList<Integer> list, int index, int value) {
        if (index < 0 || index > list.size()) {
            System.out.println("Invalid position.");
            return;
        }

        list.add(index, value);
    }

    public static void search(LinkedList<Integer> list, int value) {
        int index = list.indexOf(value);

        if (index != -1) {
            System.out.println(value + " found at index " + index);
        } else {
            System.out.println(value + " not found.");
        }
    }

    public static int calculateSum(LinkedList<Integer> list) {
        int sum = 0;

        for (int number : list) {
            sum += number;
        }

        return sum;
    }

    public static void reverseList(LinkedList<Integer> list) {
        Collections.reverse(list);
    }

    public static void findMiddle(LinkedList<Integer> list) {
        if (list.isEmpty()) {
            System.out.println("List is empty.");
            return;
        }

        int middleIndex = list.size() / 2;

        System.out.println("Middle element: " + list.get(middleIndex));
    }

    public static LinkedList<Integer> mergeAndSort(LinkedList<Integer> list1, LinkedList<Integer> list2) {
        LinkedList<Integer> merged = new LinkedList<>();

        merged.addAll(list1);
        merged.addAll(list2);

        Collections.sort(merged);

        return merged;
    }

    public static void removeDuplicates(LinkedList<Integer> list) {
        if (list.isEmpty()) {
            return;
        }

        ListIterator<Integer> iterator = list.listIterator();
        int previous = iterator.next();

        while (iterator.hasNext()) {
            int current = iterator.next();

            if (current == previous) {
                iterator.remove();
            } else {
                previous = current;
            }
        }
    }

    public static boolean hasCycle(Node head) {
        Node slow = head, fast = head;

        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;

            if (slow == fast) {
                return true;
            }
        }

        return false;
    }

    public static void main(String[] args) {
        LinkedList<Integer> list = new LinkedList<>();

        list.add(10);
        list.add(20);
        list.add(30);
        list.add(40);
        list.add(50);

        printList(list);

        addFirst(list, 5);
        addLast(list, 60);
        System.out.println("After adding elements: " + list);

        removeFirst(list);
        removeLast(list);
        System.out.println("After removing elements: " + list);

        insertAt(list, 2, 25);
        System.out.println("After inserting 25: " + list);

        search(list, 30);

        System.out.println("Sum: " + calculateSum(list));

        reverseList(list);
        System.out.println("After reversing: " + list);

        findMiddle(list);

        LinkedList<Integer> list1 = new LinkedList<>(), list2 = new LinkedList<>();
        Collections.addAll(list1, 40, 10, 30);
        Collections.addAll(list2, 20, 60, 50);

        System.out.println("\n=== MERGE AND SORT ===\nList 1: " + list1 + "\nList 2: " + list2);

        LinkedList<Integer> merged = mergeAndSort(list1, list2);
        System.out.println("Merged and sorted: " + merged);

        LinkedList<Integer> sortedList = new LinkedList<>();
        Collections.addAll(sortedList, 10, 10, 20, 20, 20, 30, 40, 40, 50);
        System.out.println("\n=== REMOVE DUPLICATES ===\nBefore: " + sortedList);
        removeDuplicates(sortedList);
        System.out.println("After: " + sortedList + "\n\n=== CYCLE DETECTION ===");

        Node node1 = new Node(1), node2 = new Node(2), node3 = new Node(3), node4 = new Node(4), node5 = new Node(5);

        node1.next = node2;
        node2.next = node3;
        node3.next = node4;
        node4.next = node5;
        node5.next = node3;

        System.out.println("Has cycle: " + hasCycle(node1));
    }
}
