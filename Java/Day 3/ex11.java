import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex11 {
    static public class Runner {
        public String name;
        public double time;
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Runner[] runners = new Runner[3];

        for (int i = 0; i < 3; i++) {
            runners[i] = new Runner();

            System.out.print("Enter runner name: ");
            runners[i].name = br.readLine();

            System.out.print("Enter runner time: ");
            runners[i].time = Double.parseDouble(br.readLine());
        }

        for (int i = 0; i < runners.length - 1; i++) {
            for (int j = 0; j < runners.length - 1 - i; j++) {
                if (runners[j].time > runners[j + 1].time) {
                    Runner temp = runners[j];
                    runners[j] = runners[j + 1];
                    runners[j + 1] = temp;
                }
            }
        }

        for (int i = 0; i < 3; i++) {
            System.out.println("\nRunner " + (i + 1) + "\nName: " + runners[i].name + "\nTime: " + runners[i].time);
        }
    }
}
