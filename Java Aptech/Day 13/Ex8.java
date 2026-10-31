import java.util.Deque;
import java.util.ArrayDeque;

public class Ex8 {
    public static class Queue<T> {
        private final Deque<T> inStack, outStack;

        public Queue() {
            inStack = new ArrayDeque<>();
            outStack = new ArrayDeque<>();
        }

        public void enqueue(T item) {
            inStack.push(item);
        }

        public T dequeue() {
            if (inStack.isEmpty() && outStack.isEmpty()) {
                throw new IllegalStateException("Queue is empty.");
            }

            if (outStack.isEmpty()) {
                while (!inStack.isEmpty()) {
                    outStack.push(inStack.pop());
                }
            }

            return outStack.pop();
        }

        public boolean isEmpty() {
            return inStack.isEmpty() && outStack.isEmpty();
        }

        public T peek() {
            if (inStack.isEmpty() && outStack.isEmpty()) {
                throw new IllegalStateException("Queue is empty.");
            }

            if (outStack.isEmpty()) {
                while (!inStack.isEmpty()) {
                    outStack.push(inStack.pop());
                }
            }

            return outStack.peek();
        }

        public int size() {
            return inStack.size() + outStack.size();
        }
    }

    public static void main(String[] args) {
        Queue<Integer> queue = new Queue<>();

        queue.enqueue(10);
        queue.enqueue(20);
        queue.enqueue(30);
        queue.enqueue(40);

        System.out.println("Queue size: " + queue.size() + "\nFront element: " + queue.peek() + "\nDequeued: "
                + queue.dequeue() + "\nDequeued: " + queue.dequeue());

        queue.enqueue(50);
        queue.enqueue(60);

        System.out.println("Front element: " + queue.peek() + "\nQueue size: " + queue.size());

        while (!queue.isEmpty()) {
            System.out.println("Dequeued: " + queue.dequeue());
        }

        System.out.println("Is queue empty? " + queue.isEmpty());

        try {
            queue.dequeue();
        } catch (IllegalStateException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
