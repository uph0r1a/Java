import java.util.*;
import java.io.*;

public class Ex1 {
    static class Category {
        int id, parentId, level;
        String name;
        List<Category> children;

        public Category(int id, String name, int parentId, int level) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
            this.level = level;
            this.children = new ArrayList<>();
        }
    }

    static List<Category> categories = new ArrayList<>();
    static Category root;
    static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    static Category buildTree(List<Category> list) {
        for (Category category : list) {
            category.children.clear();
        }

        Category treeRoot = null;

        for (Category category : list) {
            if (category.parentId == 0) {
                treeRoot = category;
                break;
            }
        }

        if (treeRoot == null) {
            return null;
        }

        treeRoot.level = 0;
        buildTreeRecursive(treeRoot, list);

        return treeRoot;
    }

    static void buildTreeRecursive(Category parent, List<Category> list) {
        for (Category category : list) {
            if (category.parentId == parent.id) {
                category.level = parent.level + 1;
                parent.children.add(category);
                buildTreeRecursive(category, list);
            }
        }
    }

    static boolean addCategory(int id, String name, int parentId) {
        for (Category category : categories) {
            if (category.id == id) {
                System.out.println("Error: Category ID already exists.");
                return false;
            }
        }

        Category parent = findCategoryById(root, parentId);

        if (parent == null) {
            System.out.println("Error: Parent category not found.");
            return false;
        }

        Category newCategory = new Category(id, name, parentId, parent.level + 1);

        categories.add(newCategory);
        parent.children.add(newCategory);

        System.out.println("Category added successfully.");
        return true;
    }

    static void printTree(Category current, String prefix) {
        if (current == null) {
            return;
        }

        System.out.println(prefix + current.name + " (ID: " + current.id + ")");

        for (Category child : current.children) {
            printTree(child, prefix + "    ");
        }
    }

    static int countTotalCategories(Category current) {
        if (current == null) {
            return 0;
        }

        int count = 1;

        for (Category child : current.children) {
            count += countTotalCategories(child);
        }

        return count;
    }

    static Category findCategoryByName(Category current, String name) {
        if (current == null) {
            return null;
        }

        if (current.name.equalsIgnoreCase(name)) {
            return current;
        }

        for (Category child : current.children) {
            Category result = findCategoryByName(child, name);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    static Category findCategoryById(Category current, int id) {
        if (current == null) {
            return null;
        }

        if (current.id == id) {
            return current;
        }

        for (Category child : current.children) {
            Category result = findCategoryById(child, id);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    static List<Category> getAllLeafCategories(Category current) {
        List<Category> leaves = new ArrayList<>();

        if (current == null) {
            return leaves;
        }

        if (current.children.isEmpty()) {
            leaves.add(current);
            return leaves;
        }

        for (Category child : current.children) {
            leaves.addAll(getAllLeafCategories(child));
        }

        return leaves;
    }

    static void bubbleSort(List<Category> list) {
        int n = list.size();

        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false;

            for (int j = 0; j < n - i - 1; j++) {
                if (list.get(j).name.compareToIgnoreCase(list.get(j + 1).name) > 0) {
                    Category temp = list.get(j);
                    list.set(j, list.get(j + 1));
                    list.set(j + 1, temp);

                    swapped = true;
                }
            }

            if (!swapped) {
                break;
            }
        }
    }

    static void sortAllCategories(Category current) {
        if (current == null) {
            return;
        }

        bubbleSort(current.children);

        for (Category child : current.children) {
            sortAllCategories(child);
        }
    }

    static Category binarySearchByName(List<Category> list, String name) {
        int left = 0, right = list.size() - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2, comparison = list.get(mid).name.compareToIgnoreCase(name);

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

    static boolean buildBreadcrumb(Category current, int targetId, Stack<Category> path) {

        if (current == null) {
            return false;
        }

        path.push(current);

        if (current.id == targetId) {
            return true;
        }

        for (Category child : current.children) {
            if (buildBreadcrumb(child, targetId, path)) {
                return true;
            }
        }

        path.pop();
        return false;
    }

    static void printBreadcrumb(int targetId) {
        Stack<Category> path = new Stack<>();

        if (buildBreadcrumb(root, targetId, path)) {
            System.out.println("Breadcrumb:");
            for (int i = 0; i < path.size(); i++) {
                System.out.print(path.get(i).name);

                if (i < path.size() - 1) {
                    System.out.print(" -> ");
                }
            }

            System.out.println();

        } else {
            System.out.println("Category not found.");
        }
    }

    static void levelOrderTraversal(Category root) {
        if (root == null) {
            return;
        }

        Queue<Category> queue = new LinkedList<>();
        queue.offer(root);
        System.out.println("Level-order Traversal:");

        while (!queue.isEmpty()) {
            Category current = queue.poll();
            System.out.println("Level " + current.level + ": " + current.name);

            for (Category child : current.children) {
                queue.offer(child);
            }
        }
    }

    static void displayLeafCategories() {
        List<Category> leaves = getAllLeafCategories(root);
        System.out.println("Leaf Categories:");

        for (Category category : leaves) {
            System.out.println(category.name + " (ID: " + category.id + ")");
        }
    }

    static void initializeCategories() {
        categories.add(new Category(1, "Electronics", 0, 0));
        categories.add(new Category(2, "Mobile Phones", 1, 1));
        categories.add(new Category(3, "Laptops", 1, 1));
        categories.add(new Category(4, "Accessories", 1, 1));
        categories.add(new Category(5, "Samsung", 2, 2));
        categories.add(new Category(6, "iPhone", 2, 2));
        categories.add(new Category(7, "Dell", 3, 2));
        categories.add(new Category(8, "MacBook", 3, 2));

        root = buildTree(categories);
    }

    static int readInt(String message) throws IOException {
        while (true) {
            System.out.print(message);

            try {
                return Integer.parseInt(br.readLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            } catch (NullPointerException e) {
                throw new IOException("Input stream closed.");
            }
        }
    }

    static String readString(String message) throws IOException {
        System.out.print(message);
        return br.readLine().trim();
    }

    static void displayMenu() {
        System.out.println("""
                ========== CATEGORY MANAGEMENT SYSTEM ==========
                1. Add a new category
                2. Display the category tree
                3. Search category by name
                4. Count total categories
                5. Display category breadcrumb
                6. Level-order traversal
                7. Display all leaf categories
                8. Binary Search in a parent's children
                9. Sort all categories alphabetically
                0. Exit
                =================================================""");

    }

    public static void main(String[] args) throws IOException {
        boolean isExit = false;
        initializeCategories();
        System.out.println("Category Management System Started.");

        while (!isExit) {
            displayMenu();
            int choice = readInt("Enter your choice: ");

            switch (choice) {
                case 1 -> {
                    int id = readInt("Enter category ID: "), parentId = readInt("Enter parent ID: ");
                    String name = readString("Enter category name: ");
                    addCategory(id, name, parentId);
                }
                case 2 -> {
                    System.out.println("\nCategory Tree:");
                    printTree(root, "");
                }
                case 3 -> {
                    String name = readString("Enter category name: ");
                    Category result = findCategoryByName(root, name);

                    if (result != null) {
                        System.out.println("Category found:");
                        System.out.println("ID: " + result.id);
                        System.out.println("Name: " + result.name);
                        System.out.println("Parent ID: " + result.parentId);
                        System.out.println("Level: " + result.level);

                    } else {
                        System.out.println("Category not found.");
                    }
                }
                case 4 -> {
                    int total = countTotalCategories(root);
                    System.out.println("Total categories: " + total);
                }
                case 5 -> {
                    int id = readInt("Enter category ID: ");
                    printBreadcrumb(id);
                }
                case 6 -> levelOrderTraversal(root);
                case 7 -> displayLeafCategories();
                case 8 -> {
                    int parentId = readInt("Enter parent category ID: ");
                    Category parent = findCategoryById(root, parentId);

                    if (parent == null) {
                        System.out.println("Parent category not found.");
                        break;
                    }

                    bubbleSort(parent.children);

                    String name = readString("Enter child category name to search: ");
                    Category result = binarySearchByName(parent.children, name);

                    if (result != null) {
                        System.out.println("Category found: " + result.name + " (ID: " + result.id + ")");
                    } else {
                        System.out.println("Category not found.");
                    }
                }
                case 9 -> {
                    sortAllCategories(root);
                    System.out.println("All categories sorted alphabetically.");
                }
                case 0 -> {
                    System.out.println("Exiting program.");
                    br.close();
                    isExit = true;
                }
                default -> System.out.println("Invalid choice. Try again.");
            }
        }
    }
}
