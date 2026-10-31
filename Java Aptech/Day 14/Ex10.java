import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

public class Ex10 {
    public static String longestPalindrome(String str) {
        String longest = "";

        for (int i = 0; i < str.length(); i++) {
            String odd = expandAroundCenter(str, i, i), even = expandAroundCenter(str, i, i + 1);

            if (odd.length() > longest.length()) {
                longest = odd;
            }

            if (even.length() > longest.length()) {
                longest = even;
            }
        }

        return longest;
    }

    public static String expandAroundCenter(String str, int left, int right) {
        while (left >= 0 && right < str.length() && str.charAt(left) == str.charAt(right)) {
            left--;
            right++;
        }

        return str.substring(left + 1, right);
    }

    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter a string: ");
        String input = reader.readLine(), result = longestPalindrome(input);

        if (result.length() >= 2) {
            System.out.println("Longest palindromic substring: " + result);
        } else {
            System.out.println("No palindromic substring found.");
        }
    }
}
