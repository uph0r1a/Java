import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex8 {
    static class Temperature {
        private double ftemp;

        public Temperature(double ftemp) {
            this.ftemp = ftemp;
        }

        public double getFahrenheit() {
            return ftemp;
        }

        public void setFahrenheit(double ftemp) {
            this.ftemp = ftemp;
        }

        public double getCelsius() {
            return ((double) 5 / 9) * (ftemp - 32);
        }

        public double getKelvin() {
            return getCelsius() + 273;
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter temperature: ");
        double temperature = Double.parseDouble(br.readLine());

        Temperature temp = new Temperature(temperature);

        System.out.println("Celsius: " + temp.getCelsius() + "\nKelvin: " + temp.getKelvin());
    }
}
