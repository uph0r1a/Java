import java.util.HashMap;

public class ex12 {
    static class StringOperations {
        public static int WordCount(String str) {
            if (str == null || str.trim().isEmpty()) {
                return 0;
            }
            return str.trim().split("\\s+").length;
        }

        public static String arrayToString(char[] arr) {
            return new String(arr);
        }

        public static char mostFrequent(String str) {
            HashMap<Character, Integer> mp = new HashMap<>();
            int maxCount = 0;
            char ans = '\0';

            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (c == ' ')
                    continue;

                mp.put(c, mp.getOrDefault(c, 0) + 1);
                if (mp.get(c) > maxCount) {
                    maxCount = mp.get(c);
                    ans = c;
                }
            }
            return ans;
        }

        public static String replaceSubstring(String str1, String str2, String str3) {
            return str1.replace(str2, str3);
        }
    }

    public static void main(String[] args) {
        String string1 = "the dog jumped over the fence", string2 = "the", string3 = "that";
        char[] c = { 'Q', 'm', '7', 'X', 'a', '!', 'P', '2', 'k', 'Z', '#', 'r', '8', 'L', 'n', '$' };

        System.out.println("Word Count: " + StringOperations.WordCount(string1) + "\nMost Frequent Character: "
                + StringOperations.mostFrequent(string1) + "\nArray to String: " + StringOperations.arrayToString(c)
                + "\nReplaced String: " + StringOperations.replaceSubstring(string1, string2, string3));
    }
}