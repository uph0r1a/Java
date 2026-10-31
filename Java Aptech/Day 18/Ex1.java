import java.util.*;
import java.io.*;

public class Ex1 {

    static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    static TreeNode root;
    static List<TreeNode> flatData = new ArrayList<>();
    static List<Doctor> doctors = new ArrayList<>();
    static HashMap<String, Patient> patients = new HashMap<>();
    static Queue<Patient> appointmentQueue = new LinkedList<>();

    static class TreeNode {
        int id, parentId;
        String name, position;
        double revenue;
        List<TreeNode> children;

        public TreeNode(int id, String name, String position, int parentId, double revenue) {
            this.id = id;
            this.name = name;
            this.position = position;
            this.parentId = parentId;
            this.revenue = revenue;
            this.children = new ArrayList<>();
        }
    }

    static class Patient {
        String id, name, diagnosis;
        int age;

        public Patient(String id, String name, int age, String diagnosis) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.diagnosis = diagnosis;
        }

        public void display() {
            System.out.println("Patient ID: " + id);
            System.out.println("Name: " + name);
            System.out.println("Age: " + age);
            System.out.println("Diagnosis: " + diagnosis);
        }
    }

    static class Doctor {
        String id, name, department;
        double salary;

        public Doctor(String id, String name, String department, double salary) {
            this.id = id;
            this.name = name;
            this.department = department;
            this.salary = salary;
        }

        public void display() {
            System.out.printf("%-10s %-20s %-20s %,.2f%n", id, name, department, salary);
        }
    }

    static TreeNode buildTree(List<TreeNode> list) {
        if (list == null || list.isEmpty()) {
            System.out.println("Error: Organization data is empty.");
            return null;
        }

        TreeNode treeRoot = null;
        for (TreeNode node : list) {
            node.children.clear();

            if (node.parentId == 0) {
                if (treeRoot != null) {
                    System.out.println("Error: Multiple root nodes found.");
                    return null;
                }

                treeRoot = node;
            }
        }

        if (treeRoot == null) {
            System.out.println("Error: Root node not found.");
            return null;
        }

        Set<Integer> ids = new HashSet<>();
        for (TreeNode node : list) {
            if (!ids.add(node.id)) {
                System.out.println("Error: Duplicate organization ID " + node.id);
                return null;
            }
        }

        Set<Integer> attached = new HashSet<>(), path = new HashSet<>();

        attached.add(treeRoot.id);
        path.add(treeRoot.id);
        buildTreeRecursive(treeRoot, list, attached, path);

        if (attached.size() != list.size()) {
            System.out.println("Warning: Some organization nodes could not be connected.");

            for (TreeNode node : list) {
                if (!attached.contains(node.id)) {
                    System.out.println("Unconnected node: " + node.name + " (ID: " + node.id + ")");
                }
            }
        }

        return treeRoot;
    }

    static void buildTreeRecursive(TreeNode parent, List<TreeNode> list, Set<Integer> attached, Set<Integer> path) {
        for (TreeNode node : list) {
            if (node.parentId == parent.id) {
                if (path.contains(node.id)) {
                    System.out.println("Cycle detected at: " + node.name);
                    continue;
                }

                if (attached.contains(node.id)) {
                    continue;
                }

                parent.children.add(node);
                attached.add(node.id);
                path.add(node.id);
                buildTreeRecursive(node, list, attached, path);
                path.remove(node.id);
            }
        }
    }

    static void preOrder(TreeNode current, String prefix) {
        if (current == null) {
            return;
        }

        System.out.println(prefix + current.name + " (" + current.position + ")");

        for (TreeNode child : current.children) {
            preOrder(child, prefix + "    ");
        }
    }

    static void postOrder(TreeNode current, String prefix) {
        if (current == null) {
            return;
        }

        for (TreeNode child : current.children) {
            postOrder(child, prefix + "    ");
        }

        System.out.println(prefix + current.name + " (" + current.position + ")");
    }

    static void inOrder(TreeNode current, String prefix) {
        if (current == null) {
            return;
        }

        if (!current.children.isEmpty()) {
            inOrder(current.children.get(0), prefix + "    ");
        }

        System.out.println(prefix + current.name + " (" + current.position + ")");

        for (int i = 1; i < current.children.size(); i++) {
            inOrder(current.children.get(i), prefix + "    ");
        }
    }

    static void levelOrder(TreeNode treeRoot) {
        if (treeRoot == null) {
            System.out.println("Organization tree is empty.");
            return;
        }

        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(treeRoot);
        System.out.println("Level-order Traversal:");

        while (!queue.isEmpty()) {
            TreeNode current = queue.poll();

            System.out.println(current.name + " (" + current.position + ")");

            for (TreeNode child : current.children) {
                queue.offer(child);
            }
        }
    }

    static void displayAllTraversals() {
        if (root == null) {
            System.out.println("Organization tree is empty.");
            return;
        }

        System.out.println("\n========== PRE-ORDER ==========");
        preOrder(root, "");

        System.out.println("\n========== POST-ORDER ==========");
        postOrder(root, "");

        System.out.println("\n========== IN-ORDER ==========");
        inOrder(root, "");

        System.out.println("\n========== LEVEL-ORDER ==========");
        levelOrder(root);
    }

    static void registerAppointment(String patientId) {
        Patient patient = patients.get(patientId);

        if (patient == null) {
            System.out.println("Error: Patient ID does not exist.");
            return;
        }

        appointmentQueue.offer(patient);
        System.out.println("Appointment registered for " + patient.name);
    }

    static void processNextPatient() {
        if (appointmentQueue.isEmpty()) {
            System.out.println("Appointment queue is empty.");
            return;
        }

        Patient patient = appointmentQueue.poll();
        System.out.println("\nProcessing patient:");
        patient.display();
    }

    static void displayAppointmentQueue() {
        if (appointmentQueue.isEmpty()) {
            System.out.println("Appointment queue is empty.");
            return;
        }

        System.out.println("\nPatients waiting for appointments:");

        for (Patient patient : appointmentQueue) {
            System.out.println(patient.id + " - " + patient.name);
        }
    }

    static class ReceptionCounter implements Runnable {
        int counterNumber;
        Queue<Patient> queue;

        public ReceptionCounter(int counterNumber, Queue<Patient> queue) {
            this.counterNumber = counterNumber;
            this.queue = queue;
        }

        @Override
        public void run() {
            while (true) {
                Patient patient;

                synchronized (queue) {
                    patient = queue.poll();
                }

                if (patient == null) {
                    break;
                }

                System.out.println("\nCounter " + counterNumber + " [" + Thread.currentThread().getName() + "]"
                        + " is processing " + patient.name + " (" + patient.id + ")");
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Counter " + counterNumber + " was interrupted.");
                    break;
                }

                System.out.println("Counter " + counterNumber + " finished processing " + patient.name);
            }
        }
    }

    static void processMultipleCounters(int numberOfCounters) {
        if (numberOfCounters <= 0) {
            System.out.println("Invalid number of counters.");
            return;
        }

        if (appointmentQueue.isEmpty()) {
            System.out.println("Appointment queue is empty.");
            return;
        }

        List<Queue<Patient>> counterQueues = new ArrayList<>();
        for (int i = 0; i < numberOfCounters; i++) {
            counterQueues.add(new LinkedList<>());
        }

        int index = 0;
        while (!appointmentQueue.isEmpty()) {
            Patient patient = appointmentQueue.poll();
            counterQueues.get(index).offer(patient);
            index = (index + 1) % numberOfCounters;
        }

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < numberOfCounters; i++) {
            ReceptionCounter counter = new ReceptionCounter(i + 1, counterQueues.get(i));
            Thread thread = new Thread(counter, "Reception-" + (i + 1));

            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Main thread was interrupted.");
                return;
            }
        }

        System.out.println("\nAll reception counters have finished.");
    }

    static void addPatient(Patient patient) {
        if (patient == null) {
            System.out.println("Invalid patient information.");
            return;
        }

        if (patients.containsKey(patient.id)) {
            System.out.println("Error: Patient ID already exists.");
            return;
        }

        patients.put(patient.id, patient);
        System.out.println("Patient added successfully.");
    }

    static void searchPatientByHashMap(String patientId) {
        Patient patient = patients.get(patientId);

        if (patient == null) {
            System.out.println("Patient not found.");
            return;
        }

        System.out.println("\nPatient found:");
        patient.display();
    }

    static void displayAllPatients() {
        if (patients.isEmpty()) {
            System.out.println("No patients available.");
            return;
        }

        System.out.println("\n========== PATIENT LIST ==========");
        for (Patient patient : patients.values()) {
            System.out.println(patient.id + " - " + patient.name + " - " + patient.age);
        }
    }

    static void selectionSortDoctors() {
        int n = doctors.size();

        for (int i = 0; i < n - 1; i++) {
            int maxIndex = i;
            for (int j = i + 1; j < n; j++) {
                if (doctors.get(j).salary > doctors.get(maxIndex).salary) {
                    maxIndex = j;
                }
            }

            Doctor temp = doctors.get(i);
            doctors.set(i, doctors.get(maxIndex));
            doctors.set(maxIndex, temp);
        }
    }

    static void displayDoctors() {
        if (doctors.isEmpty()) {
            System.out.println("No doctors available.");
            return;
        }

        System.out.printf("\n========== DOCTORS SORTED BY SALARY ==========\n%-10s %-20s %-20s %s%n", "ID", "Name",
                "Department", "Salary");

        for (Doctor doctor : doctors) {
            doctor.display();
        }
    }

    static List<Patient> convertPatientsToList() {
        return new ArrayList<>(patients.values());
    }

    static void selectionSortPatients(List<Patient> list) {
        int n = list.size();

        for (int i = 0; i < n - 1; i++) {
            int minIndex = i;

            for (int j = i + 1; j < n; j++) {
                if (list.get(j).id.compareToIgnoreCase(list.get(minIndex).id) < 0) {
                    minIndex = j;
                }
            }

            Patient temp = list.get(i);
            list.set(i, list.get(minIndex));
            list.set(minIndex, temp);
        }
    }

    static Patient binarySearchPatient(List<Patient> list, String patientId) {
        int left = 0, right = list.size() - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2, comparison = list.get(mid).id.compareToIgnoreCase(patientId);

            if (comparison == 0) {
                return list.get(mid);
            }

            if (comparison < 0) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        return null;
    }

    static void searchPatientByBinarySearch(String patientId) {
        if (patients.isEmpty()) {
            System.out.println("No patients available.");
            return;
        }

        List<Patient> patientList = convertPatientsToList();
        selectionSortPatients(patientList);
        Patient result = binarySearchPatient(patientList, patientId);

        if (result == null) {
            System.out.println("Patient not found.");
        } else {
            System.out.println("\nPatient found using Binary Search:");
            result.display();
        }
    }

    static String reverseString(String text) {
        if (text == null) {
            return "";
        }

        Stack<Character> stack = new Stack<>();
        for (int i = 0; i < text.length(); i++) {
            stack.push(text.charAt(i));
        }

        StringBuilder reversed = new StringBuilder();
        while (!stack.isEmpty()) {
            reversed.append(stack.pop());
        }

        return reversed.toString();
    }

    static double calculateTotalRevenue(TreeNode current) {
        if (current == null) {
            return 0;
        }

        double total = current.revenue;
        for (TreeNode child : current.children) {
            total += calculateTotalRevenue(child);
        }

        return total;
    }

    static TreeNode findDepartmentById(TreeNode current, int id) {
        if (current == null) {
            return null;
        }

        if (current.id == id) {
            return current;
        }

        for (TreeNode child : current.children) {
            TreeNode result = findDepartmentById(child, id);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    static void displayDepartmentRevenue(int departmentId) {
        if (root == null) {
            System.out.println("Organization tree is empty.");
            return;
        }

        TreeNode department = findDepartmentById(root, departmentId);

        if (department == null) {
            System.out.println("Department or node not found.");
            return;
        }

        double total = calculateTotalRevenue(department);

        System.out.printf("Total revenue for %s: %,.2f VND%n", department.name, total);
    }

    static void initializeOrganization() {
        flatData.add(new TreeNode(1, "Nguyen Van An", "CEO", 0, 0));
        flatData.add(new TreeNode(2, "Tran Van Binh", "Hospital Director", 1, 0));
        flatData.add(new TreeNode(10, "Le Thi Hoa", "Cardiology Head", 2, 50000000));
        flatData.add(new TreeNode(11, "Pham Van Nam", "Pediatrics Head", 2, 35000000));
        flatData.add(new TreeNode(12, "Hoang Thi Lan", "Neurology Head", 2, 42000000));
        flatData.add(new TreeNode(101, "Doctor Nguyen", "Doctor", 10, 0));
        flatData.add(new TreeNode(102, "Doctor Tran", "Doctor", 10, 0));
        flatData.add(new TreeNode(103, "Doctor Le", "Doctor", 11, 0));
        flatData.add(new TreeNode(104, "Doctor Pham", "Doctor", 12, 0));
        root = buildTree(flatData);
    }

    static void initializeDoctors() {
        doctors.add(new Doctor("D001", "Nguyen Minh", "Cardiology", 45000000));
        doctors.add(new Doctor("D002", "Tran Hoang", "Pediatrics", 38000000));
        doctors.add(new Doctor("D003", "Le Quang", "Neurology", 42000000));
        doctors.add(new Doctor("D004", "Pham Tuan", "Cardiology", 50000000));
        doctors.add(new Doctor("D005", "Hoang Nam", "Pediatrics", 35000000));
    }

    static void initializePatients() {
        addPatient(new Patient("P001", "Nguyen Van A", 25, "Flu"));
        addPatient(new Patient("P002", "Tran Thi B", 30, "Headache"));
        addPatient(new Patient("P003", "Le Van C", 45, "Hypertension"));
        addPatient(new Patient("P004", "Pham Thi D", 35, "Diabetes"));
        addPatient(new Patient("P005", "Hoang Van E", 50, "Heart Disease"));
    }

    static void initializeSystem() {
        initializeOrganization();
        initializeDoctors();
        initializePatients();
    }

    static String readString(String message) throws IOException {
        while (true) {
            System.out.print(message);
            String input = br.readLine();

            if (input == null) {
                throw new EOFException("Input stream closed.");
            }

            input = input.trim();

            if (!input.isEmpty()) {
                return input;
            }

            System.out.println("Input cannot be empty.");
        }
    }

    static int readInt(String message) throws IOException {
        while (true) {
            System.out.print(message);

            String input = br.readLine();

            if (input == null) {
                throw new EOFException("Input stream closed.");
            }

            try {
                return Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    static double readDouble(String message) throws IOException {
        while (true) {
            System.out.print(message);

            String input = br.readLine();

            if (input == null) {
                throw new EOFException("Input stream closed.");
            }

            try {
                double value = Double.parseDouble(input.trim());

                if (value < 0) {
                    System.out.println("Value cannot be negative.");
                    continue;
                }

                return value;
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    static void displayMenu() {
        System.out.println("""
                ==============================================
                        HOSPITAL MANAGEMENT SYSTEM
                ==============================================
                1. Display organization tree traversals
                2. Register patient appointment
                3. Process next patient
                4. Display appointment queue
                5. Add a new patient
                6. Search patient using HashMap
                7. Display all patients
                8. Search patient using Binary Search
                9. Sort doctors by salary
                10. Reverse HOSPITAL using Stack
                11. Calculate department revenue
                12. Process patients using multiple counters
                0. Exit
                =============================================""");

    }

    public static void main(String[] args) {
        try {
            initializeSystem();
            System.out.println("Hospital Management System initialized successfully.");

            while (true) {
                displayMenu();
                int choice = readInt("Enter your choice: ");

                switch (choice) {
                    case 1 -> displayAllTraversals();
                    case 2 -> {
                        String patientId = readString("Enter patient ID: ");
                        registerAppointment(patientId);
                    }
                    case 3 -> processNextPatient();
                    case 4 -> displayAppointmentQueue();
                    case 5 -> {
                        String id = readString("Enter patient ID: ");
                        String name = readString("Enter patient name: ");
                        String diagnosis = readString("Enter diagnosis: ");
                        int age = readInt("Enter patient age: ");

                        if (age <= 0) {
                            System.out.println("Invalid patient age.");
                            break;
                        }

                        Patient patient = new Patient(id, name, age, diagnosis);
                        addPatient(patient);
                    }
                    case 6 -> {
                        String id = readString("Enter patient ID: ");
                        searchPatientByHashMap(id);
                    }
                    case 7 -> displayAllPatients();
                    case 8 -> {
                        String id = readString("Enter patient ID: ");
                        searchPatientByBinarySearch(id);
                    }
                    case 9 -> {
                        selectionSortDoctors();
                        displayDoctors();
                    }
                    case 10 -> {
                        String original = "HOSPITAL";
                        String reversed = reverseString(original);

                        System.out.println("Original string: " + original);
                        System.out.println("Reversed string: " + reversed);
                    }
                    case 11 -> {
                        System.out.println("\nAvailable organization nodes:");
                        preOrder(root, "");

                        int id = readInt("Enter department/node ID: ");
                        displayDepartmentRevenue(id);
                    }
                    case 12 -> {
                        int counters = readInt("Enter number of reception counters: ");
                        processMultipleCounters(counters);
                    }
                    case 0 -> {
                        System.out.println("Hospital Management System closed.");
                        br.close();
                        return;
                    }
                    default -> System.out.println("Invalid choice. Please try again.");
                }
            }
        } catch (EOFException e) {
            System.out.println("\nInput ended. Hospital Management System closed.");
        } catch (IOException e) {
            System.out.println("Input/Output error: " + e.getMessage());
        }
    }
}
