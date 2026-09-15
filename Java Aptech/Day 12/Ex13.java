public class Ex13 {
    public static class TreeNode {
        private int data;
        private TreeNode left, right;

        public TreeNode(int data) {
            this.data = data;
        }
    }

    public static TreeNode buildTree(int[] arr, int lo, int hi) {
        if (lo > hi) {
            return null;
        }

        int mid = (lo + hi) / 2;
        TreeNode root = new TreeNode(arr[mid]);

        root.left = buildTree(arr, lo, mid - 1);
        root.right = buildTree(arr, mid + 1, hi);

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

    public static void preOrder(TreeNode root) {
        if (root == null) {
            return;
        }

        System.out.print(root.data + " ");
        preOrder(root.left);
        preOrder(root.right);
    }

    public static void postOrder(TreeNode root) {
        if (root == null) {
            return;
        }

        postOrder(root.left);
        postOrder(root.right);
        System.out.print(root.data + " ");
    }

    public static void main(String[] args) {
        int[] arr = { 10, 20, 30, 40, 50, 60, 70, 80 };
        TreeNode root = buildTree(arr, 0, arr.length - 1);

        System.out.print("In-order: ");
        inOrder(root);
        System.out.println();

        System.out.print("Pre-order: ");
        preOrder(root);
        System.out.println();

        System.out.print("Post-order: ");
        postOrder(root);
        System.out.println();
    }
}