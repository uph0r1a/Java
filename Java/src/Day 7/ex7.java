import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ex7 {
    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        double[][] saleFigure = new double[6][4];

        for (int i = 0; i < 6; i++) {
            System.out.println("\nDivision " + (i + 1));

            for (int j = 0; j < 4; j++) {
                while (true) {
                    try {
                        System.out.print("Enter sales figure for Quarter " + (j + 1) + ": ");
                        saleFigure[i][j] = Double.parseDouble(br.readLine());

                        if (saleFigure[i][j] < 0) {
                            System.out.println("Sales figure cannot be negative.");
                        } else {
                            break;
                        }

                    } catch (Exception e) {
                        System.out.println("Invalid input. Try again.");
                    }
                }
            }
        }

        double previousCompanyTotal = 0;

        for (int q = 0; q < 4; q++) {
            System.out.println("\n====================================\nQuarter " + (q + 1)
                    + "\n====================================");

            double quarterTotal = 0;
            double highestSale = saleFigure[0][q];
            int highestDivision = 0;

            for (int d = 0; d < 6; d++) {
                System.out.print("Division " + (d + 1) + ": " + saleFigure[d][q]);

                if (q != 0) {
                    double diff = saleFigure[d][q] - saleFigure[d][q - 1];

                    if (diff >= 0)
                        System.out.print(" (Increase: +" + diff + ")");
                    else
                        System.out.print(" (Decrease: " + diff + ")");
                }

                System.out.println();

                quarterTotal += saleFigure[d][q];

                if (saleFigure[d][q] > highestSale) {
                    highestSale = saleFigure[d][q];
                    highestDivision = d;
                }
            }

            System.out.println("------------------------------------\nTotal Sales: " + quarterTotal);

            if (q != 0) {
                double companyDiff = quarterTotal - previousCompanyTotal;

                if (companyDiff >= 0)
                    System.out.println("Company Increase: +" + companyDiff);
                else
                    System.out.println("Company Decrease: " + companyDiff);
            }

            System.out.println("Average Sales: " + (quarterTotal / 6) + "\nHighest Sales: Division "
                    + (highestDivision + 1) + " (" + highestSale + ")");
            previousCompanyTotal = quarterTotal;
        }
    }
}