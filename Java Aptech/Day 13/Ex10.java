import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ArrayDeque;

public class Ex10 {
    private static final String FILE_NAME = "warehouse.txt";

    static class Crate {
        private final String id;
        private final int weight;

        public Crate(String id, int weight) {
            this.id = id;
            this.weight = weight;
        }

        public String getId() {
            return id;
        }

        public int getWeight() {
            return weight;
        }

        @Override
        public String toString() {
            return id + ": " + weight + "kg";
        }
    }

    @SuppressWarnings("unchecked")
    private static final Deque<Crate>[] shelves = new Deque[3];
    private static final Map<String, Integer> registry = new HashMap<>();
    private static final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    public static void initializeShelves() {
        for (int i = 0; i < shelves.length; i++) {
            shelves[i] = new ArrayDeque<>();
        }
    }

    private static void push(int shelfIndex, Crate crate) {
        shelves[shelfIndex].addLast(crate);
        registry.put(crate.getId(), shelfIndex);
    }

    private static Crate pop(int shelfIndex) {
        return shelves[shelfIndex].removeLast();
    }

    private static Crate peek(int shelfIndex) {
        return shelves[shelfIndex].peekLast();
    }

    private static void moveCrate(int source, int destination) {
        Crate crate = pop(source);
        push(destination, crate);
    }

    private static String readLine(String prompt) throws IOException {
        System.out.print(prompt);
        return reader.readLine();
    }

    private static int readPositiveWeight() throws IOException {
        while (true) {
            String input = readLine("Enter crate weight: ");

            if (input == null) {
                throw new IOException("Input stream closed.");
            }

            try {
                int weight = Integer.parseInt(input.trim());

                if (weight > 0) {
                    return weight;
                }

                System.out.println("Weight must be a positive number.");

            } catch (NumberFormatException e) {
                System.out.println("Invalid weight. Please enter a positive integer.");
            }
        }
    }

    private static int readMenuChoice() throws IOException {
        String input = readLine("Your choice: ");

        if (input == null) {
            return 7;
        }

        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static void addCrate() throws IOException {
        System.out.println("\n=== ADD A CRATE ===");

        String id;
        while (true) {
            id = readLine("Enter crate ID: ");

            if (id == null) {
                return;
            }

            id = id.trim();

            if (id.isEmpty()) {
                System.out.println("Crate ID cannot be blank.");
            } else if (registry.containsKey(id)) {
                System.out.println("This crate ID already exists. Try another ID.");
            } else {
                break;
            }
        }

        int weight = readPositiveWeight();
        Crate crate = new Crate(id, weight);

        shelves[0].addFirst(crate);
        registry.put(id, 0);

        System.out.println("Crate " + id + " added to Shelf 1.");
    }

    public static void displayShelves() {
        System.out.println("\n=== SHELF STATUS ===");

        for (int i = 0; i < shelves.length; i++) {
            System.out.print("Shelf " + (i + 1) + ": [");

            Iterator<Crate> iterator = shelves[i].descendingIterator();

            while (iterator.hasNext()) {
                System.out.print(iterator.next());

                if (iterator.hasNext()) {
                    System.out.print(", ");
                }
            }

            System.out.println("]");
        }
    }

    public static void sortCrates() {
        System.out.println("\n=== SORTING CRATES ===");

        if (!shelves[1].isEmpty()) {
            System.out.println("Cannot sort: Shelf 2 must be empty before sorting.");
            return;
        }

        sortRecursive();
        System.out.println("Crates have been sorted successfully.");
    }

    private static void sortRecursive() {
        if (shelves[0].isEmpty()) {
            return;
        }

        Crate current = pop(0);
        registry.put(current.getId(), -1);
        sortRecursive();

        while (!shelves[2].isEmpty() && peek(2).getWeight() < current.getWeight()) {
            moveCrate(2, 1);
        }

        push(2, current);

        while (!shelves[1].isEmpty()) {
            moveCrate(1, 2);
        }
    }

    public static void retrieveCrate() throws IOException {
        System.out.println("\n=== RETRIEVE A CRATE ===");

        String id = readLine("Enter crate ID to retrieve: ");

        if (id == null) {
            return;
        }

        id = id.trim();

        if (!registry.containsKey(id)) {
            System.out.println("Crate not found.");
            return;
        }

        if (!shelves[1].isEmpty()) {
            System.out.println("Cannot retrieve: Shelf 2 is currently occupied.");
            return;
        }

        int shelfIndex = registry.get(id);

        System.out.println("Retrieve " + id + " from Shelf " + (shelfIndex + 1) + ".");

        while (!shelves[shelfIndex].isEmpty() && !peek(shelfIndex).getId().equals(id)) {
            moveCrate(shelfIndex, 1);
        }

        Crate retrieved = pop(shelfIndex);
        registry.remove(retrieved.getId());

        System.out.println("Crate " + retrieved.getId() + " retrieved.");

        while (!shelves[1].isEmpty()) {
            moveCrate(1, shelfIndex);
        }

        System.out.println("\nShelf state after retrieval:");
        displayShelves();
    }

    public static void saveShelfState() {
        System.out.println("\n=== SAVE SHELF STATE ===");

        ArrayList<String> fileContents = new ArrayList<>();
        fileContents.add("WAREHOUSE_V1");

        for (int i = 0; i < shelves.length; i++) {
            Iterator<Crate> iterator = shelves[i].descendingIterator();

            while (iterator.hasNext()) {
                Crate crate = iterator.next();
                String encodedId = Base64.getEncoder().encodeToString(crate.getId().getBytes(StandardCharsets.UTF_8));

                fileContents.add(i + "|" + encodedId + "|" + crate.getWeight());
            }
        }

        try (FileWriter writer = new FileWriter(FILE_NAME)) {
            for (String line : fileContents) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }

            System.out.println("Shelf state saved successfully to " + FILE_NAME);
        } catch (IOException e) {
            System.out.println("Error saving file: " + e.getMessage());
        }
    }

    public static void loadShelfState() {
        System.out.println("\n=== LOAD SHELF STATE ===");

        @SuppressWarnings("unchecked")
        Deque<Crate>[] loadedShelves = new Deque[3];

        for (int i = 0; i < loadedShelves.length; i++) {
            loadedShelves[i] = new ArrayDeque<>();
        }

        Map<String, Integer> loadedRegistry = new HashMap<>();

        try (BufferedReader fileReader = new BufferedReader(new FileReader(FILE_NAME))) {
            String header = fileReader.readLine();

            if (!"WAREHOUSE_V1".equals(header)) {
                throw new IOException("Invalid file format.");
            }

            String line;
            while ((line = fileReader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|", -1);
                if (parts.length != 3) {
                    throw new IOException("Invalid crate record.");
                }

                int shelfIndex, weight;
                String id;

                try {
                    shelfIndex = Integer.parseInt(parts[0]);
                    weight = Integer.parseInt(parts[2]);
                    byte[] decodedId = Base64.getDecoder().decode(parts[1]);
                    id = new String(decodedId, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid crate data.");
                }

                if (shelfIndex < 0 || shelfIndex >= 3) {
                    throw new IOException("Invalid shelf number.");
                }

                if (id.trim().isEmpty()) {
                    throw new IOException("Crate ID cannot be blank.");
                }

                if (weight <= 0) {
                    throw new IOException("Crate weight must be positive.");
                }

                if (loadedRegistry.containsKey(id)) {
                    throw new IOException("Duplicate crate ID: " + id);
                }

                Crate crate = new Crate(id, weight);

                loadedShelves[shelfIndex].addFirst(crate);
                loadedRegistry.put(id, shelfIndex);
            }

            for (int i = 0; i < shelves.length; i++) {
                shelves[i].clear();
                shelves[i].addAll(loadedShelves[i]);
            }

            registry.clear();
            registry.putAll(loadedRegistry);

            System.out.println("Shelf state loaded successfully from " + FILE_NAME);

            displayShelves();
        } catch (IOException e) {
            System.out.println(
                    "Unable to load shelf state: " + e.getMessage() + "\nCurrent shelf state has not been changed.");

        } catch (SecurityException e) {
            System.out.println("Unable to access the save file: " + e.getMessage()
                    + "\nCurrent shelf state has not been changed.");
        }
    }

    public static void main(String[] args) {
        initializeShelves();
        boolean isExit = false;

        System.out.println("Warehouse Crate Management System");

        try {
            while (!isExit) {
                System.out.println("""
                        \n=== WAREHOUSE CRATE MANAGEMENT ===
                        1. Add a Crate
                        2. Display Shelf Status
                        3. Sort Crates
                        4. Retrieve a Crate by Code
                        5. Save Shelf State to File
                        6. Load Shelf State from File
                        7. Exit
                        """);

                int choice = readMenuChoice();
                switch (choice) {
                    case 1 -> addCrate();

                    case 2 -> displayShelves();
                    case 3 -> sortCrates();
                    case 4 -> retrieveCrate();
                    case 5 -> saveShelfState();
                    case 6 -> loadShelfState();
                    case 7 -> {
                        System.out.println("Exiting warehouse system...");
                        isExit = true;
                    }
                    default -> System.out.println("Invalid choice. Please select an option from 1 to 7.");
                }
            }
        } catch (IOException e) {
            System.out.println("\nInput error: " + e.getMessage());
        }
    }
}
