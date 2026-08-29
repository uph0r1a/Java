import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;

public class Ex1 {
    public static class Category {
        private int id, parentId, level;
        private String name;
        private List<Category> children;

        public Category(int id, String name, int parentId) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
            this.level = 0;
            this.children = new ArrayList<>();
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getParentId() {
            return parentId;
        }

        public void setParentId(int parentId) {
            this.parentId = parentId;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public List<Category> getChildren() {
            return children;
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }

        @Override
        public String toString() {
            return "ID: " + id + "\nName: " + name + "\nParent ID: " + parentId + "\nLevel: " + level;
        }
    }

    public static class CategoryManager {
        private List<Category> flatList;
        private Category root;

        public CategoryManager() {
            this.flatList = new ArrayList<>();
            this.root = null;
        }

        public void addCategory(int id, String name, int parentId) {
            flatList.add(new Category(id, name, parentId));
            System.out.println(name + " (id=" + id + ") added successfully");
        }

        public void buildTree() {
            for (Category c : flatList) {
                if (c.getParentId() == 0) {
                    root = c;
                    root.setLevel(0);
                    break;
                }
            }

            if (root == null) {
                System.out.println("No root category found");
                return;
            }
            attachChildren(root);
            sortTreeRecursively(root);
        }

        private void attachChildren(Category parent) {
            parent.getChildren().clear();
            for (Category c : flatList) {
                if (c.getParentId() == parent.getId()) {
                    c.setLevel(parent.getLevel() + 1);
                    parent.getChildren().add(c);
                    attachChildren(c);
                }
            }
        }

        public Category getRoot() {
            return root;
        }

        public List<Category> getFlatList() {
            return flatList;
        }

        public void printTree(Category node, String prefix) {
            if (node == null)
                return;
            System.out.println(prefix + "- " + node.getName());
            List<Category> children = node.getChildren();
            for (Category child : children) {
                printTree(child, prefix + "   ");
            }
        }

        public int countTotalCategories(Category node) {
            if (node == null)
                return 0;
            int count = 1;
            for (Category child : node.getChildren()) {
                count += countTotalCategories(child);
            }
            return count;
        }

        public Category findCategoryByName(Category node, String name) {
            if (node == null)
                return null;
            if (node.getName().equalsIgnoreCase(name))
                return node;

            for (Category child : node.getChildren()) {
                Category found = findCategoryByName(child, name);
                if (found != null)
                    return found;
            }
            return null;
        }

        public List<Category> getAllLeafCategories(Category node) {
            List<Category> leaves = new ArrayList<>();
            collectLeaves(node, leaves);
            return leaves;
        }

        private void collectLeaves(Category node, List<Category> leaves) {
            if (node == null)
                return;
            if (node.isLeaf()) {
                leaves.add(node);
                return;
            }
            for (Category child : node.getChildren()) {
                collectLeaves(child, leaves);
            }
        }

        public void sortChildrenBubbleSort(Category node) {
            if (node == null)
                return;
            List<Category> children = node.getChildren();
            int n = children.size();

            for (int i = 0; i < n - 1; i++) {
                for (int j = 0; j < n - 1 - i; j++) {
                    if (children.get(j).getName().compareToIgnoreCase(children.get(j + 1).getName()) > 0) {
                        Category temp = children.get(j);
                        children.set(j, children.get(j + 1));
                        children.set(j + 1, temp);
                    }
                }
            }
        }

        public void sortTreeRecursively(Category node) {
            if (node == null)
                return;
            sortChildrenBubbleSort(node);
            for (Category child : node.getChildren()) {
                sortTreeRecursively(child);
            }
        }

        public Category binarySearchByName(List<Category> sortedList, String name) {
            int low = 0;
            int high = sortedList.size() - 1;

            while (low <= high) {
                int mid = (low + high) / 2;
                String midName = sortedList.get(mid).getName();
                int cmp = midName.compareToIgnoreCase(name);

                if (cmp == 0) {
                    return sortedList.get(mid);
                } else if (cmp < 0) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return null;
        }

        public void printBreadcrumb(String name) {
            Category target = findCategoryByName(root, name);
            if (target == null) {
                System.out.println(name + " not found");
                return;
            }

            Stack<Category> stack = new Stack<>();
            Category current = target;

            while (current != null) {
                stack.push(current);
                if (current.getParentId() == 0)
                    break;
                current = findCategoryById(root, current.getParentId());
            }

            StringBuilder sb = new StringBuilder();
            while (!stack.isEmpty()) {
                sb.append(stack.pop().getName());
                if (!stack.isEmpty())
                    sb.append(" > ");
            }
            System.out.println("Breadcrumb: " + sb);
        }

        private Category findCategoryById(Category node, int id) {
            if (node == null)
                return null;
            if (node.getId() == id)
                return node;
            for (Category child : node.getChildren()) {
                Category found = findCategoryById(child, id);
                if (found != null)
                    return found;
            }
            return null;
        }

        public void levelOrderTraversal(Category root) {
            if (root == null) {
                System.out.println("Tree is empty.");
                return;
            }

            Queue<Category> queue = new LinkedList<>();
            queue.offer(root);

            while (!queue.isEmpty()) {
                Category current = queue.poll();
                System.out.println("Level " + current.getLevel() + ": " + current.getName());

                for (Category child : current.getChildren()) {
                    queue.offer(child);
                }
            }
        }
    }

    private static void handleAddCategory() throws IOException {
        System.out.print("Enter new category id: ");
        int id = Integer.parseInt(br.readLine().trim());

        System.out.print("Enter category name: ");
        String name = br.readLine().trim();

        System.out.print("Enter parent id: ");
        int parentId = Integer.parseInt(br.readLine().trim());

        manager.addCategory(id, name, parentId);
        manager.buildTree();
        System.out.println("Tree rebuilt successfully.");
    }

    private static void handleShowTree() {
        System.out.println("\n--- Category Tree ---");
        manager.printTree(manager.getRoot(), "");
    }

    private static void handleSearchByName() throws IOException {
        System.out.print("Enter category name to search: ");
        String name = br.readLine().trim();

        Category found = manager.findCategoryByName(manager.getRoot(), name);
        if (found != null) {
            System.out.println("Found: " + found);
        } else {
            System.out.println("Category \"" + name + "\" was not found.");
        }
    }

    private static void handleCountTotal() {
        int total = manager.countTotalCategories(manager.getRoot());
        System.out.println("Total number of categories: " + total);
    }

    private static void handleBreadcrumb() throws IOException {
        System.out.print("Enter category name for the breadcrumb path: ");
        String name = br.readLine().trim();
        manager.printBreadcrumb(name);
    }

    private static void handleLevelOrder() {
        System.out.println("\n--- Level-order Traversal (Queue) ---");
        manager.levelOrderTraversal(manager.getRoot());
    }

    private static CategoryManager manager = new CategoryManager();
    private static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static void main(String[] args) throws IOException {
        manager.addCategory(1, "Electronics", 0);
        manager.addCategory(2, "Mobile Phones", 1);
        manager.addCategory(3, "Laptops", 1);
        manager.addCategory(4, "Accessories", 1);
        manager.addCategory(5, "Samsung", 2);
        manager.addCategory(6, "iPhone", 2);
        manager.addCategory(7, "Dell", 3);
        manager.addCategory(8, "MacBook", 3);
        manager.buildTree();

        Category mobilePhones = manager.findCategoryByName(manager.getRoot(), "Mobile Phones");
        if (mobilePhones != null) {
            System.out.println("\n--- Binary Search Demo (Part 4) ---");
            Category result = manager.binarySearchByName(mobilePhones.getChildren(), "iPhone");
            System.out
                    .println(result != null ? "Binary search found: " + result.getName() : "Binary search: not found");
        }

        boolean isExit = false;
        while (!isExit) {
            System.out.print("""
                    =====================================
                        PRODUCT CATEGORY TREE MANAGEMENT
                    =====================================
                    1. Add a new category
                    2. Display the whole category tree
                    3. Search a category by name
                    4. Count total number of categories
                    5. Show breadcrumb path of a category
                    6. Level-order traversal (Queue)
                    7. Exit
                    =====================================
                    Enter your choice:\s""");

            try {
                int choice = Integer.parseInt(br.readLine().trim());
                switch (choice) {
                    case 1 -> handleAddCategory();
                    case 2 -> handleShowTree();
                    case 3 -> handleSearchByName();
                    case 4 -> handleCountTotal();
                    case 5 -> handleBreadcrumb();
                    case 6 -> handleLevelOrder();
                    case 7 -> isExit = true;
                    default -> System.out.println("Invalid choice, please try again.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number between 1 and 7.");
            } catch (IOException e) {
                System.out.println("Error reading input: " + e.getMessage());
            }

            System.out.println();
        }
    }
}