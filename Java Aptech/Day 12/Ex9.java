import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Ex9 {
    public static class LRUCache<K, V> {
        private static class Node<K, V> {
            K key;
            V value;
            Node<K, V> prev;
            Node<K, V> next;

            Node(K key, V value) {
                this.key = key;
                this.value = value;
            }
        }

        private final int capacity;
        private final Map<K, Node<K, V>> map;
        private final Node<K, V> head, tail;

        public LRUCache(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Capacity must be greater than 0");
            }

            this.capacity = capacity;
            this.map = new HashMap<>();

            head = new Node<>(null, null);
            tail = new Node<>(null, null);

            head.next = tail;
            tail.prev = head;
        }

        public V get(K key) {
            Node<K, V> node = map.get(key);

            if (node == null) {
                return null;
            }

            moveToFront(node);
            return node.value;
        }

        public void put(K key, V value) {
            Node<K, V> node = map.get(key);

            if (node != null) {
                node.value = value;
                moveToFront(node);
                return;
            }

            if (map.size() == capacity) {
                Node<K, V> lru = tail.prev;
                remove(lru);
                map.remove(lru.key);
            }

            Node<K, V> newNode = new Node<>(key, value);
            map.put(key, newNode);
            addToFront(newNode);
        }

        private void moveToFront(Node<K, V> node) {
            remove(node);
            addToFront(node);
        }

        private void addToFront(Node<K, V> node) {
            node.next = head.next;
            node.prev = head;

            head.next.prev = node;
            head.next = node;
        }

        private void remove(Node<K, V> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
        }

        public void printCache() {
            Node<K, V> current = head.next;

            System.out.print("[ ");

            while (current != tail) {
                System.out.print("(" + current.key + ", " + current.value + ")");

                if (current.next != tail) {
                    System.out.print(", ");
                }
                current = current.next;
            }
            System.out.println(" ]");
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter cache capacity: ");
        int capacity;
        while (true) {
            try {
                capacity = Integer.parseInt(br.readLine());
                if (capacity > 0) {
                    break;
                }
                System.out.print("Capacity must be greater than 0\nRe-enter cache capacity: ");
            } catch (Exception e) {
                System.out.print("Invalid input\nRe-enter cache capacity: ");
            }
        }

        LRUCache<Integer, String> cache = new LRUCache<>(capacity);

        System.out.print("Enter number of operations: ");
        int n;
        while (true) {
            try {
                n = Integer.parseInt(br.readLine());
                if (n >= 0) {
                    break;
                }
                System.out.print("Invalid number of operations\nRe-enter number of operations: ");
            } catch (Exception e) {
                System.out.print("Invalid input\nRe-enter number of operations: ");
            }
        }

        for (int i = 0; i < n; i++) {
            System.out.print("Operation " + (i + 1) + " - 1) get 2) put: ");
            int opType;
            while (true) {
                try {
                    opType = Integer.parseInt(br.readLine());
                    if (opType == 1 || opType == 2) {
                        break;
                    }
                    System.out.print("Invalid option\nRe-enter operation type: ");
                } catch (Exception e) {
                    System.out.print("Invalid input\nRe-enter operation type: ");
                }
            }

            System.out.print("Enter key: ");
            int key;
            while (true) {
                try {
                    key = Integer.parseInt(br.readLine());
                    break;
                } catch (Exception e) {
                    System.out.print("Invalid input\nRe-enter key: ");
                }
            }

            if (opType == 1) {
                String result = cache.get(key);
                System.out.println(result != null ? "get(" + key + ") -> " + result : "get(" + key + ") -> not found");
            } else {
                System.out.print("Enter value: ");
                String value = br.readLine().strip();
                cache.put(key, value);
                System.out.println("put(" + key + ", " + value + ")");
            }

            cache.printCache();
        }
    }
}