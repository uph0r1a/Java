package module;

public class InvalidBatteryLifeException extends RuntimeException {
    public InvalidBatteryLifeException(String message) {
        super(message);
    }
}