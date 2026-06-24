package module;

public class DuplicateDeviceIdException extends RuntimeException {
    public DuplicateDeviceIdException(String message) {
        super(message);
    }
}
