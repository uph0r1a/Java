import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ex3 {
    public static String capitalizeSentences(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Pattern pattern = Pattern.compile("(^|\\.\\s+|\\?\\s+|!\\s+)([a-z])");
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1) + matcher.group(2).toUpperCase());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static void main(String[] args) {
        String input = "hello. my name is Joe. what is your name?";
        System.out.println(capitalizeSentences(input));
    }
}
