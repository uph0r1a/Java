public class Ex12 {
    public static class TreeNode {
        private int data;
        private TreeNode left, right;

        public TreeNode(int data) {
            this.data = data;
            this.left = null;
            this.right = null;
        }
    }

    public static TreeNode insert(TreeNode root, int value) {
        if (root == null) {
            return new TreeNode(value);
        }

        if (value < root.data) {
            root.left = insert(root.left, value);
        } else {
            root.right = insert(root.right, value);
        }
        return root;
    }

    public static TreeNode remove(TreeNode root, int value) {
        if (root == null) {
            return null;
        }

        if (value < root.data) {
            root.left = remove(root.left, value);
        } else if (value > root.data) {
            root.right = remove(root.right, value);
        } else {
            if (root.left == null && root.right == null) {
                return null;
            }

            if (root.left == null) {
                return root.right;
            }

            if (root.right == null) {
                return root.left;
            }

            TreeNode successor = root.right;
            while (successor.left != null) {
                successor = successor.left;
            }

            root.data = successor.data;
            root.right = remove(root.right, successor.data);
        }
        return root;
    }

    public static void preOrder(TreeNode root) {
        if (root == null) {
            return;
        }

        System.out.print(root.data + " ");

        preOrder(root.left);
        preOrder(root.right);
    }

    public static void main(String[] args) {
        int[] values = { 45, 23, 67, 12, 34, 89, 50 };
        TreeNode root = null;

        for (int value : values) {
            root = insert(root, value);
        }

        System.out.print("Pre-order before removal: ");
        preOrder(root);
        System.out.println();

        root = remove(root, 34);

        System.out.print("Pre-order after removal: ");
        preOrder(root);
        System.out.println();
    }
}