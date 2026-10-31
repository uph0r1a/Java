import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

public class Ex5 {
    public static class Request implements Comparable<Request> {
        private final String requestID;
        private final int priority;
        private final boolean poisonPill;

        public Request(String requestID, int priority) {
            this(requestID, priority, false);
        }

        public Request(String requestID, int priority, boolean poisonPill) {
            this.requestID = requestID;
            this.priority = priority;
            this.poisonPill = poisonPill;
        }

        public String getRequestID() {
            return requestID;
        }

        public int getPriority() {
            return priority;
        }

        public boolean isPoisonPill() {
            return poisonPill;
        }

        @Override
        public int compareTo(Request other) {
            if (this.poisonPill && !other.poisonPill) {
                return 1;
            }

            if (!this.poisonPill && other.poisonPill) {
                return -1;
            }

            return Integer.compare(other.priority, this.priority);
        }
    }

    public static class Worker extends Thread {
        private final BlockingQueue<Request> queue;

        public Worker(String name, BlockingQueue<Request> queue) {
            super(name);
            this.queue = queue;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Request request = queue.take();

                    if (request.isPoisonPill()) {
                        System.out.println(getName() + " is shutting down.");
                        break;
                    }

                    System.out.println(getName() + " is processing " + request.getRequestID() + " (Priority: "
                            + request.getPriority() + ")");
                    Thread.sleep(1000);
                    System.out.println(getName() + " finished processing " + request.getRequestID());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(getName() + " was interrupted.");
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int numberOfWorkers = 3;
        BlockingQueue<Request> queue = new PriorityBlockingQueue<>();
        Worker[] workers = new Worker[numberOfWorkers];

        for (int i = 0; i < numberOfWorkers; i++) {
            workers[i] = new Worker("Worker-" + (i + 1), queue);
            workers[i].start();
        }

        queue.put(new Request("Request-A", 2));
        queue.put(new Request("Request-B", 5));
        queue.put(new Request("Request-C", 1));
        queue.put(new Request("Request-D", 4));
        queue.put(new Request("Request-E", 3));

        for (int i = 0; i < numberOfWorkers; i++) {
            queue.put(new Request("POISON_PILL", Integer.MIN_VALUE, true));
        }

        for (Worker worker : workers) {
            worker.join();
        }

        System.out.println("All requests have been processed.");
    }
}
