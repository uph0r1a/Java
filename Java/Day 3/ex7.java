import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class ex7 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        String[] name = new String[3];

        for (int i = 0; i < 3; i++) {
            System.out.print("Enter name: ");
            name[i] = br.readLine();
        }

        Arrays.sort(name);
        System.out.println(Arrays.toString(name));

    }
}
