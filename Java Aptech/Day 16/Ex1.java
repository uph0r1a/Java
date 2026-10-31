import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Ex1 {
    static class FileNode {
        String name;
        boolean isDirectory;
        List<FileNode> children;
        long size;

        public FileNode(String name, boolean isDirectory, long size) {
            this.name = name;
            this.isDirectory = isDirectory;
            this.size = size;
            this.children = new ArrayList<>();
        }

        public void addChild(FileNode child) {
            if (isDirectory) {
                children.add(child);
            } else {
                System.out.println("Cannot add a child to a file.");
            }
        }

        public void addFile(String name, long size) {
            FileNode file = new FileNode(name, false, size);
            addChild(file);
        }

        public void addDirectory(String name) {
            FileNode directory = new FileNode(name, true, 0);
            addChild(directory);
        }
    }

    public static void printFileSystem(FileNode node, String prefix) {
        Stack<FileNode> nodeStack = new Stack<>();
        Stack<String> prefixStack = new Stack<>();

        nodeStack.push(node);
        prefixStack.push(prefix);

        while (!nodeStack.isEmpty()) {
            FileNode current = nodeStack.pop();
            String currentPrefix = prefixStack.pop();

            if (current.isDirectory) {
                System.out.println(currentPrefix + "[DIR] " + current.name);
            } else {
                System.out.println(currentPrefix + "[FILE] " + current.name + " (" + current.size + " bytes)");
            }

            for (int i = current.children.size() - 1; i >= 0; i--) {
                nodeStack.push(current.children.get(i));
                prefixStack.push(currentPrefix + "    ");
            }
        }
    }

    public static void main(String[] args) {
        FileNode root = new FileNode("Project", true, 0);

        root.addDirectory("src");
        root.addDirectory("resources");

        FileNode src = root.children.get(0);
        src.addFile("Main.java", 2048);

        System.out.println("File System Structure:");
        printFileSystem(root, "");
    }
}
