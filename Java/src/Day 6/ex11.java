import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex11 {
    static class Temperature {
        private double temp;

        public Temperature(double temp) {
            this.temp = temp;
        }

        public double getTemp() {
            return temp;
        }

        public void setTemp(double temp) {
            this.temp = temp;
        }

        public boolean isEthylFreezing() {
            return (temp < -173);
        }

        public boolean isEthylBoiling() {
            return (temp > 172);
        }

        public boolean isOxygenFreezing() {
            return (temp < -362);
        }

        public boolean isOxygenBoiling() {
            return (temp > -306);
        }

        public boolean isWaterFreezing() {
            return (temp < 32);
        }

        public boolean isWaterBoiling() {
            return (temp > 212);
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter temperature: ");
        double temp = Double.parseDouble(br.readLine());

        Temperature temperature = new Temperature(temp);

        if (temperature.isEthylFreezing()) {
            System.out.println("Ethyl is freezing");
        }
        if (temperature.isEthylBoiling()) {
            System.out.println("Ethyl is boiling");
        }
        if (temperature.isOxygenFreezing()) {
            System.out.println("Oxygen is freezing");
        }
        if (temperature.isOxygenBoiling()) {
            System.out.println("Oxygen is boiling");
        }
        if (temperature.isWaterFreezing()) {
            System.out.println("Water is freezing");
        }
        if (temperature.isWaterBoiling()) {
            System.out.println("Water is boiling");
        }
    }
}
