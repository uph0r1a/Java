import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex10_11 {
    public static void profit(int ns, double pp, double pc, double sp, double sc) {
        double p = ((ns * sp) - sc) - ((ns * pp) + pc);

        if (p >= 0) {
            System.out.println(" profit: " + p);
        } else {
            System.out.println(" loss: " + Math.abs(p));
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter number of stock sale: ");
        int ss = Integer.parseInt(br.readLine());

        int[] ns = new int[ss];
        double[] pp = new double[ss], pc = new double[ss], sp = new double[ss], sc = new double[ss];
        for (int i = 0; i < ss; i++) {
            System.out.print("Stock " + (i + 1) + ": \nEnter number of shares: ");
            ns[i] = Integer.parseInt(br.readLine());

            System.out.print("Enter purchased price per share: ");
            pp[i] = Double.parseDouble(br.readLine());

            System.out.print("Enter purchased commission paid: ");
            pc[i] = Double.parseDouble(br.readLine());

            System.out.print("Enter sale price per share: ");
            sp[i] = Double.parseDouble(br.readLine());

            System.out.print("Enter sale commission paid: ");
            sc[i] = Double.parseDouble(br.readLine());
        }

        for (int i = 0; i < ss; i++) {
            System.out.print("Stock " + (i + 1));
            profit(ns[i], pp[i], pc[i], sp[i], sc[i]);
        }
    }
}
