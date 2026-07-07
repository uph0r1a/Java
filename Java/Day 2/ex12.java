import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex12 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter your favourite city: ");
        String city = br.readLine();

        System.out.print("•  The number of characters in the city name: " + city.length()
                + "\n•  The name of the city in all uppercase letters: " + city.toUpperCase()
                + "\n•  The name of the city in all lowercase letters: " + city.toLowerCase()
                + "\n•  The first character in the name of the city: " + city.charAt(0));
    }
}
