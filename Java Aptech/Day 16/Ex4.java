public class Ex4 {
    public static class SupportTicket {
        private String ticketID, customerName, issue, priority;

        public SupportTicket(String ticketID, String customerName, String issue, String priority) {
            this.ticketID = ticketID;
            this.customerName = customerName;
            this.issue = issue;
            this.priority = priority;
        }

        public String getTicketID() {
            return ticketID;
        }

        public void setTicketID(String ticketID) {
            this.ticketID = ticketID;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }

        public String getIssue() {
            return issue;
        }

        public void setIssue(String issue) {
            this.issue = issue;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }
    }

    public static void main(String[] args) {
        
    }
}
