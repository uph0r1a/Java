public class Ex3 {
    public static class AdvancedComparion<T extends Comparable<T>> {
        private T a, b, c;

        public AdvancedComparion(T a, T b, T c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }

        public T maximum() {
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
        AdvancedComparion<Integer> advancedComparionInt = new AdvancedComparion<>(1, 2, 3);
        AdvancedComparion<Float> advancedComparionFloat = new AdvancedComparion<>(1.1f, 2.2f, 3.3f);
        AdvancedComparion<String> advancedComparionStr = new AdvancedComparion<>("a", "b", "c");

        System.out.println("Max int: " + advancedComparionInt.maximum() + "\nMax float: "
                + advancedComparionFloat.maximum() + "\nMax string: " + advancedComparionStr.maximum());
    }
}