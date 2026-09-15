import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Ex11 {
    public static class TreeNode {
        private String data;
        private List<TreeNode> children;

        public TreeNode(String data) {
            this.data = data;
            this.children = new ArrayList<>();
        }
    }

    public static List<TreeNode> buildTree(String[][] categories) {
        Map<String, TreeNode> lookup = new HashMap<>();
        List<TreeNode> topLevelCategories = new ArrayList<>();

        for (String[] pair : categories) {
            String category = pair[0];
            String subcategory = pair[1];
            TreeNode categoryNode = lookup.get(category);

            if (categoryNode == null) {
                categoryNode = new TreeNode(category);

                lookup.put(category, categoryNode);
                topLevelCategories.add(categoryNode);
            }

            TreeNode subcategoryNode = new TreeNode(subcategory);
            categoryNode.children.add(subcategoryNode);
        }
        return topLevelCategories;
    }

    public static void printTree(TreeNode node, String indent) {
        System.out.println(indent + node.data);

        for (TreeNode child : node.children) {
            printTree(child, indent + "- ");
        }
    }

    public static void main(String[] args) {
        String[][] categories = {
                { "Electronics", "Phones" },
                { "Electronics", "Laptops" },
                { "Home", "Furniture" },
                { "Home", "Appliances" }
        };
        List<TreeNode> topLevelCategories = buildTree(categories);
        for (TreeNode topLevel : topLevelCategories) {
            printTree(topLevel, "");
        }
    }
}