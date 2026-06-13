package module;

public class Ticket {
    public enum Priority {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum Status {
        OPEN,
        IN_PROGRESS,
        RESOLVED
    }

    private int id;
    private String title, reporterName;
    private Priority priority;
    private Status status;

    public Ticket() {
        this.id = 0;
        this.title = "";
        this.reporterName = "";
        this.priority = Priority.LOW;
        this.status = Status.OPEN;
    }

    public Ticket(int id, String title, String reporterName, Priority priority, Status status) {
        this.id = id;
        this.title = title;
        this.reporterName = reporterName;
        this.priority = priority;
        this.status = status;
    }

    public int getID() {
        return id;
    }

    public void setStatus(String status) {
        this.status = Status.valueOf(status.toUpperCase());
    }

    public Priority getPriority() {
        return priority;
    }

    public String display() {
        return "\nID: " + id + "\nTitle: " + title + "\nReporter: " + reporterName + "\nPriority: "
                + priority.toString().toLowerCase()
                + "\nStatus: " + status.toString().toLowerCase();
    }
}
