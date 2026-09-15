public class Ex5 {
    public static class Request {
        private String requestID;
        private int priority;
        public Request(String requestID, int priority) {
            this.requestID = requestID;
            this.priority = priority;
        }
        public String getRequestID() {
            return requestID;
        }
        public void setRequestID(String requestID) {
            this.requestID = requestID;
        }
        public int getPriority() {
            return priority;
        }
        public void setPriority(int priority) {
            this.priority = priority;
        }
    }

    
    public static void main(String[] args) {
        
    }
}
