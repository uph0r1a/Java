package javafx.util;

/** Stand-in for the JavaFX class of the same name, only so the library-free runner can compile and test pure converters (TODO 3.78). */
public abstract class StringConverter<T> {
    public abstract String toString(T object);

    public abstract T fromString(String string);
}
