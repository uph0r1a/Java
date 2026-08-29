public class Ex3 {
    public static class AdvancedComparion<T extends Comparable<T>> {

        public T maximum(T a, T b, T c) {
            T max = a;
            if (b.compareTo(max) > 0) {
                max = b;
            }
            if (c.compareTo(max) > 0) {
                max = c;
            }
            return max;
        }
    }

    public static void main(String[] args) {
        AdvancedComparion<Integer> advancedComparionInt = new AdvancedComparion<>();
        AdvancedComparion<Float> advancedComparionFloat = new AdvancedComparion<>();
        AdvancedComparion<String> advancedComparionStr = new AdvancedComparion<>();

        System.out.println("Max int: " + advancedComparionInt.maximum(1, 2, 3) + "\nMax float: "
                + advancedComparionFloat.maximum(1.1f, 2.2f, 3.3f) + "\nMax string: "
                + advancedComparionStr.maximum("a", "b", "c"));
    }
}