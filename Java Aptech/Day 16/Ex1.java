import java.util.List;

public class Ex1 {
    public static class FileNode {
        private String name;
        private boolean isDirectory;
        private List<FileNode> children;
        private long size;

        public FileNode(String name, boolean isDirectory,long size) {
            this.name = name;
            this.isDirectory = isDirectory;
            this.size = size;
        }

        public void addChild(FileNode child) {
            children.add(child);
            System.out.println("File added successfully");
        }

        public void addFile(String name, long size) {

        }

        public void addDirectory(String name){

        }

        public void printFileSystem(FileNode node, String prefix){
            
        }
    }

    public static void main(String[] args) {

    }
}
