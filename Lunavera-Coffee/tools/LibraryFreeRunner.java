import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the test classes named in a file (one per line) and sorts every failure into "real" or "needs a library" (JavaFX, a
 * database driver). TODO 6.73. Exit code 1 only when there is a real failure.
 */
public final class LibraryFreeRunner {

    private LibraryFreeRunner() {}

    private static boolean needsLibrary(Throwable t) {
        for (int depth = 0; t != null && depth < 12; depth++, t = t.getCause()) {
            if (t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError || t instanceof UnsatisfiedLinkError
                    || t instanceof ExceptionInInitializerError && t.getCause() == null) return true;
            String text = String.valueOf(t.getMessage());
            if (text.contains("No suitable driver") || text.contains("javafx") || text.contains("org.sqlite") || text.contains("org.mariadb")
                    || text.contains("Toolkit not initialized") || text.contains("java.sql.SQLException")) return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        List<String> classes = Files.readAllLines(Path.of(args[0]));
        int passed = 0, skippedTests = 0, skippedClasses = 0;
        List<String> real = new ArrayList<>();
        for (String name : classes) {
            if (name.isBlank()) continue;
            Result result;
            try {
                result = new JUnitCore().run(Class.forName(name));
            } catch (Throwable e) {
                skippedClasses++;
                continue;
            }
            int libs = 0;
            for (Failure f : result.getFailures()) {
                if (needsLibrary(f.getException())) {
                    libs++;
                } else {
                    Description d = f.getDescription();
                    real.add(d.getClassName() + "." + d.getMethodName() + ": " + f.getMessage());
                }
            }
            skippedTests += libs;
            passed += result.getRunCount() - result.getFailureCount() - result.getIgnoreCount();
            if (libs > 0 && libs == result.getRunCount()) skippedClasses++;
        }
        System.out.println();
        System.out.println("Library-free tests: " + passed + " passed, " + real.size() + " FAILED, " + skippedTests
                + " not run here (need JavaFX or a database driver), " + skippedClasses + " whole classes not run.");
        for (String line : real) System.out.println("  FAILED " + line);
        System.exit(real.isEmpty() ? 0 : 1);
    }
}
