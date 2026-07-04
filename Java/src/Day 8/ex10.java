public class ex10 {
    static class FuelGauge {
        private int fuel;
        private final int MAX_FUEL = 15;

        public FuelGauge() {
            fuel = 0;
        }

        public FuelGauge(int fuel) {
            if (fuel >= 0 && fuel <= MAX_FUEL)
                this.fuel = fuel;
            else
                this.fuel = 0;
        }

        public int getFuel() {
            return fuel;
        }

        public void incrementFuel() {
            if (fuel < MAX_FUEL)
                fuel++;
        }

        public void decrementFuel() {
            if (fuel > 0)
                fuel--;
        }
    }

    static class Odometer {
        private int mileage, milesSinceFuelBurn;
        private FuelGauge fuelGauge;
        private final int MAX_MILEAGE = 999999;

        public Odometer(int mileage, FuelGauge fuelGauge) {
            this.mileage = mileage;
            this.fuelGauge = fuelGauge;
            milesSinceFuelBurn = 0;
        }

        public int getMileage() {
            return mileage;
        }

        public void incrementMileage() {
            mileage++;
            if (mileage > MAX_MILEAGE)
                mileage = 0;
            milesSinceFuelBurn++;

            if (milesSinceFuelBurn == 24) {
                fuelGauge.decrementFuel();
                milesSinceFuelBurn = 0;
            }
        }
    }

    public static void main(String[] args) {
        FuelGauge fuelGauge = new FuelGauge();
        Odometer odometer = new Odometer(0, fuelGauge);

        while (fuelGauge.getFuel() < 15) {
            fuelGauge.incrementFuel();
        }

        System.out.println("Car filled with fuel.\n-----------------------------");
        while (fuelGauge.getFuel() > 0) {
            odometer.incrementMileage();
            System.out.printf("Mileage: %,6d miles   Fuel: %2d gallons%n", odometer.getMileage(), fuelGauge.getFuel());
        }

        System.out.println("-----------------------------\nThe car has run out of fuel.");
    }
}
