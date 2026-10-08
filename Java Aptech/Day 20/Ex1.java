import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Ex1 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static class Car implements Serializable {
        private static final long serialVersionUID = 1L;
        private String carModel, make;
        private int year;

        public Car() {
        }

        public Car(String carModel, String make, int year) {
            this.carModel = carModel;
            this.make = make;
            this.year = year;
        }

        public String getCarModel() {
            return carModel;
        }

        public void setCarModel(String carModel) {
            this.carModel = carModel;
        }

        public String getMake() {
            return make;
        }

        public void setMake(String make) {
            this.make = make;
        }

        public int getYear() {
            return year;
        }

        public void setYear(int year) {
            this.year = year;
        }

        @Override
        public String toString() {
            return "\nCar Model: " + carModel + "\nMake: " + make + "\nYear: " + year;
        }
    }

    public static class CarManager {
        private List<Car> cars;

        public CarManager() {
            this.cars = new ArrayList<>();
        }

        public boolean isValidCarModel(String model) {
            return model.matches("^[A-Z0-9]+$");
        }

        public void inputCarInfo() throws IOException {
            for (int i = 0; i < 3; i++) {
                System.out.println("Enter car model: ");
                String model;
                while (true) {
                    try {
                        model = br.readLine().strip();
                        if (isValidCarModel(model)) {
                            break;
                        }
                        System.out.println("Invalid car model\nRe-enter car model: ");
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                }

                System.out.println("Enter car make: ");
                String make = br.readLine().strip();

                System.out.println("Enter year: ");
                int year;
                while (true) {
                    try {
                        year = Integer.parseInt(br.readLine());
                        if (year >= 1900 && year <= LocalDate.now().getYear()) {
                            break;
                        }
                        System.out.println("Invalid year\nRe-enter year: ");
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                }

                cars.add(new Car(model, make, year));
            }
        }

        public void serializeCars() {
            try (OutputStream fOut = Files.newOutputStream(Path.of("/files/cars.txt"))) {
                ObjectOutputStream out = new ObjectOutputStream(fOut);
                out.writeObject(cars);
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        public List<Car> deserializeCars() {
            try (InputStream fIn = Files.newInputStream(Path.of("/files/cars.txt"))) {
                ObjectInputStream in = new ObjectInputStream(fIn);
                @SuppressWarnings("unchecked")
                List<Car> deserializedList = (List<Car>) in.readObject();

                return deserializedList;
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
            return null;
        }

        public void displayCars(){
            for (Car car : cars) {
                car.toString();
            }
        }
    }

    public static void main(String[] args) {

    }
}
