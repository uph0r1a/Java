public class ex2 {
    public static int countWord(String str) {
        return str.trim().split("\\s+").length;
    }

    public static void main(String[] args) {
        String str = "Four  score  and  seven  years  ago";
        System.out.println(countWord(str));
    }
}
