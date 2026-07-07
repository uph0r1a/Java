import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class ex16 {
    private static final Map<Character, String> textToMorse = new HashMap<>();
    private static final Map<String, Character> morseToText = new HashMap<>();

    private static final String[][] MAPPINGS = { { "A", ".-" }, { "B", "-..." }, { "C", "-.-." }, { "D", "-.." },
            { "E", "." }, { "F", "..-." }, { "G", "--." }, { "H", "...." }, { "I", ".." }, { "J", ".---" },
            { "K", "-.-" }, { "L", ".-.." }, { "M", "--" }, { "N", "-." }, { "O", "---" }, { "P", ".--." },
            { "Q", "--.-" }, { "R", ".-." }, { "S", "..." }, { "T", "-" }, { "U", "..-" }, { "V", "...-" },
            { "W", ".--" }, { "X", "-..-" }, { "Y", "-.--" }, { "Z", "--.." }, { "0", "-----" }, { "1", ".----" },
            { "2", "..---" }, { "3", "...--" }, { "4", "....-" }, { "5", "....." }, { "6", "-...." }, { "7", "--..." },
            { "8", "---.." }, { "9", "----." }, { " ", "/" } };

    static {
        for (String[] m : MAPPINGS) {
            textToMorse.put(m[0].charAt(0), m[1]);
        }

        for (Map.Entry<Character, String> entry : textToMorse.entrySet()) {
            morseToText.put(entry.getValue(), entry.getKey());
        }
    }

    public static String toMorse(String text) {
        StringBuilder result = new StringBuilder();

        for (char c : text.toUpperCase().toCharArray()) {
            String morse = textToMorse.get(c);
            if (morse != null) {
                result.append(morse).append(" ");
            }
        }
        return result.toString().trim();
    }

    public static String toText(String morse) {
        StringBuilder result = new StringBuilder();
        String[] words = morse.split(" / ");

        for (String word : words) {
            String[] letters = word.split(" ");

            for (String letter : letters) {
                Character c = morseToText.get(letter);
                if (c != null) {
                    result.append(c);
                }
            }
            result.append(" ");
        }
        return result.toString().trim();
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter a str: ");
        String text = br.readLine();

        String encoded = toMorse(text);
        String decoded = toText(encoded);

        System.out.println("Text: " + text + "\nMorse: " + encoded + "\nDecoded: " + decoded);
    }
}
