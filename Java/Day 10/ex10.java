public class ex10 {
    static class Ship {
        String name, year;

        public Ship(String name, String year) {
            this.name = name;
            this.year = year;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getYear() {
            return year;
        }

        public void setYear(String year) {
            this.year = year;
        }

        @Override
        public String toString() {
            return "Ship name: " + name + "\nYear built: " + year;
        }
    }

    static class CruiseShip extends Ship {
        private int maxPassenger;

        public CruiseShip(String name, String year, int maxPassenger) {
            super(name, year);
            this.maxPassenger = maxPassenger;
        }

        public int getMaxPassenger() {
            return maxPassenger;
        }

        public void setMaxPassenger(int maxPassenger) {
            this.maxPassenger = maxPassenger;
        }

        @Override
        public String toString() {
            return "Ship name: " + name + "\nMaximum number of passengers: " + maxPassenger;
        }
    }

    static class CargoShip extends Ship {
        private int cargoCapacity;

        public CargoShip(String name, String year, int cargoCapacity) {
            super(name, year);
            this.cargoCapacity = cargoCapacity;
        }

        public int getCargoCapacity() {
            return cargoCapacity;
        }

        public void setCargoCapacity(int cargoCapacity) {
            this.cargoCapacity = cargoCapacity;
        }

        @Override
        public String toString() {
            return "Ship name: " + name + "\nCargo capacity: " + cargoCapacity;
        }

    }

    public static void main(String[] args) {
        Ship[] ships = {
                new Ship("A", "2000"),
                new CruiseShip("C", "2000", 100),
                new CargoShip("B", "2000", 10)
        };

        for (Ship ship : ships) {
            System.out.println(ship.toString());
        }
    }
}
