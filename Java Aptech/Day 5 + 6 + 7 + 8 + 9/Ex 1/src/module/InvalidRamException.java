package module;

public class InvalidRamException extends RuntimeException {
    public InvalidRamException(String message) {
        super(message);
    }
}