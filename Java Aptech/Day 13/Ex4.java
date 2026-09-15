import java.util.ArrayDeque;
import java.util.Queue;

public class Ex4 {
    public static class Task {
        private String taskId, description;

        public Task(String taskId, String description) {
            this.taskId = taskId;
            this.description = description;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static void offer(Queue<Task> tasks, Task task) {
        tasks.offer(task);
        System.out.println("Enqueued -> Task ID: " + task.getTaskId() + ", Description: " + task.getDescription());
    }

    public static void poll(Queue<Task> tasks) {
        Task t = tasks.poll();
        if (t == null) {
            System.out.println("Queue is empty");
            return;
        }
        System.out.println("Processing -> Task ID: " + t.getTaskId() + "\nTask description: " + t.getDescription());
    }

    public static void peek(Queue<Task> tasks) {
        Task t = tasks.peek();
        if (t == null) {
            System.out.println("Queue is empty");
            return;
        }
        System.out.println("Front task -> Task ID: " + t.getTaskId() + "\nTask description: " + t.getDescription());
    }

    public static void main(String[] args) {
        Queue<Task> tasks = new ArrayDeque<>();

        System.out.println("=== Enqueuing tasks ===");
        offer(tasks, new Task("T1", "Write report"));
        offer(tasks, new Task("T2", "Review code"));
        offer(tasks, new Task("T3", "Deploy release"));

        System.out.println("\n=== Peek at the front task (no removal) ===");
        peek(tasks);

        System.out.println("\n=== Processing tasks (poll) ===");
        poll(tasks);
        poll(tasks);

        System.out.println("\n=== Peek again after two removals ===");
        peek(tasks);

        System.out.println("\n=== Draining the queue ===");
        poll(tasks);
        poll(tasks);

        System.out.println("\n=== Peek on an empty queue ===");
        peek(tasks);
    }
}