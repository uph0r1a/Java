public class ex5 {
    public static boolean isPalindrome(String str, int start, int end) {
        if (start >= end) {
            return true;
        }

        if (str.charAt(start) != str.charAt(end)) {
            return false;
        }
        return isPalindrome(str, start + 1, end - 1);
    }

    public static boolean isPalindrome(String str) {
        String cleaned = str.toLowerCase().replaceAll("[^a-z0-9]", "");
        return isPalindrome(cleaned, 0, cleaned.length() - 1);
    }

    public static void main(String[] args) {
        String[] tests = { "Able was I, ere I saw Elba", "A man, a plan, a canal, Panama", "Desserts, I stressed",
                "Kayak" };

        for (String test : tests) {
            System.out.println("\"" + test + "\" -> " + (isPalindrome(test) ? "Palindrome" : "Not a palindrome"));
        }
    }
}