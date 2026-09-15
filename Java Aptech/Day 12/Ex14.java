public class Ex14 {
    public static class TreeNode {
        private int data;
        private TreeNode left, right;

        public TreeNode(int data) {
            this.data = data;
        }
    }

    public static TreeNode insert(TreeNode root, int value) {
        if (root == null) {
            return new TreeNode(value);
        }

        if (value < root.data) {
            root.left = insert(root.left, value);
        }

        else {
            root.right = insert(root.right, value);
        }

        return root;
    }

    public static void inOrder(TreeNode root) {
        if (root == null) {
            return;
        }

        inOrder(root.left);
        System.out.print(root.data + " ");
        inOrder(root.right);
    }

    public static void main(String[] args) {
        int[] values = { 50, 30, 70, 20, 40, 60, 80 };
        TreeNode root = null;

        for (int value : values) {
            root = insert(root, value);
        }

        System.out.print("In-order traversal: ");
        inOrder(root);
    }
}
