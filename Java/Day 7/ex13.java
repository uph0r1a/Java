import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ex13 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<String> boyNames = new ArrayList<>(Files.readAllLines(Path.of("files/BoyNames.txt"))),
                girlNames = new ArrayList<>(Files.readAllLines(Path.of("files/GirlNames.txt")));

        System.out.print("Enter a boy name: ");
        String boy = br.readLine().trim();

        System.out.print("Enter a girl name: ");
        String girl = br.readLine().trim();

        if (boyNames.stream().anyMatch(n -> n.equalsIgnoreCase(boy))) {
            System.out.println(
                    boy.substring(0, 1).toUpperCase() + boy.substring(1).toLowerCase() + " is among the most popular");
        }
        if (girlNames.stream().anyMatch(n -> n.equalsIgnoreCase(girl))) {
            System.out.println(girl.substring(0, 1).toUpperCase() + girl.substring(1).toLowerCase()
                    + " name is among the most popular");
        }
    }
}
