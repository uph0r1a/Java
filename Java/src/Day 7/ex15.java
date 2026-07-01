import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ex15 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        List<String> teams = new ArrayList<>(Files.readAllLines(Path.of("files/WorldSeriesWinners.txt")));
        System.out.print("Enter a team name: ");
        String team = br.readLine().trim();
        int count = 0;

        for (String t : teams) {
            if (t.equalsIgnoreCase(team)) {
                team = t;
                count++;
            }
        }
        System.out.println(team + " had won " + count + " World Series from 1903 to 2009");
    }
}
