public class ex2 {
    static class Car {
        private int yearModel, speed;
        private String make;

        public Car(int yearModel, String make) {
            this.yearModel = yearModel;
            this.make = make;
            this.speed = 0;
        }

        public int getYearModel() {
            return yearModel;
        }

        public int getSpeed() {
            return speed;
        }

        public String getMake() {
            return make;
        }

        public void accelerate() {
            this.speed += 5;
        }

        public void brake() {
            this.speed -= 5;
        }
    }

    public static void main(String[] args) {
        Car car = new Car(2000, "Ford");

        car.accelerate();
        car.accelerate();
        car.accelerate();
        car.accelerate();
        car.accelerate();

        System.out.println("Speed: " + car.getSpeed());

        car.brake();
        car.brake();
        car.brake();
        car.brake();
        car.brake();

        System.out.println("Speed: " + car.getSpeed());
    }
}
