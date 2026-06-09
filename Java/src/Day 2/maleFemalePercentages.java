import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class maleFemalePercentages {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter number of male: ");
        int male = Integer.parseInt(br.readLine());

        System.out.print("Enter number of female: ");
        int female = Integer.parseInt(br.readLine());

        System.out.print("Percentage of male: " + (double) male / (female + male) * 100 + "%\nPercentage of female: "
                + (double) female / (female + male) * 100 + "%");
    }
}
