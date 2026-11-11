import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * TODO 6.15: looks into the program folder that {@code ./mvnw -Pmodular-image package} made ({@code target/lunavera}) and says whether the
 * things that went wrong in a jlink image before are right, WITHOUT starting the program (it needs a screen). It asks the image's own
 * {@code bin/java}: which modules it holds (the program, both database drivers, slf4j, the JavaFX modules), and, for each driver, whether
 * its module descriptor says {@code provides java.sql.Driver} (without that line {@code DriverManager} reports "No suitable driver" for
 * the database, which is what the 2026-11-03 first start showed). It also checks that the launcher exists and reports the size.
 *
 * <p>Run with the JDK only, from the project folder: {@code java tools/ImageCheck.java [target/lunavera]}. Exit code 0: nothing wrong
 * (a warning may still be printed); 1: something is wrong; 2: that is not a program folder, or wrong use.
 */
public final class ImageCheck {

    /** What the image must hold for the program to start and to open either database. */
    static final List<String> REQUIRED_MODULES = List.of("com.coffeeshop.coffeeshopmanagement", "org.mariadb.jdbc", "org.xerial.sqlitejdbc",
            "org.slf4j", "javafx.base", "javafx.graphics", "javafx.controls", "javafx.fxml", "javafx.media", "java.sql", "java.desktop");
    /** The modules that must say they provide a JDBC driver. */
    static final List<String> DRIVER_MODULES = List.of("org.mariadb.jdbc", "org.xerial.sqlitejdbc");

    private static final Pattern PROVIDES_DRIVER = Pattern.compile("(?m)^\\s*provides\\s+java\\.sql\\.Driver\\s+with\\s+\\S+");
    private static final Pattern USES = Pattern.compile("(?m)^\\s*uses\\s+\\S+");

    private ImageCheck() {
    }

    record Result(List<String> lines, int exitCode) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            System.err.println("Usage: java tools/ImageCheck.java [program-folder]   (default: target/lunavera)");
            System.exit(2);
        }
        Result result = check(Path.of(args.length == 1 ? args[0] : "target/lunavera"));
        result.lines().forEach(System.out::println);
        System.exit(result.exitCode());
    }

    static Result check(Path image) throws IOException, InterruptedException {
        List<String> out = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path java = image.resolve("bin").resolve(windows ? "java.exe" : "java");
        if (!Files.isRegularFile(java)) {
            out.add("Not a program folder: " + java + " does not exist. Build it with ./mvnw -B -DskipTests -Pmodular-image clean package");
            return new Result(out, 2);
        }
        int problems = 0;

        // 1. the modules
        String listing = run(java, "--list-modules");
        Set<String> modules = new LinkedHashSet<>();
        for (String line : listing.split("\\R")) {
            String name = line.strip().split("@")[0];
            if (!name.isEmpty() && !name.contains(" ")) modules.add(name);
        }
        out.add("Modules in the image: " + modules.size());
        for (String required : REQUIRED_MODULES) {
            if (!modules.contains(required)) {
                out.add("  MISSING  " + required);
                problems++;
            }
        }

        // 2. the drivers
        for (String driver : DRIVER_MODULES) {
            if (!modules.contains(driver)) continue; // already reported as missing
            String described = run(java, "--describe-module", driver);
            if (!PROVIDES_DRIVER.matcher(described).find()) {
                out.add("  NO DRIVER  " + driver + " does not say \"provides java.sql.Driver\": DriverManager would not find it (\"No suitable driver\").");
                problems++;
            } else {
                out.add("  driver ok  " + driver);
            }
            if (driver.equals("org.mariadb.jdbc") && !USES.matcher(described).find()) {
                out.add("  WARNING    " + driver + " has no \"uses\" line: if the connector loads its plugins with ServiceLoader, connecting to MariaDB will fail.");
            }
        }

        // 3. the launcher
        boolean launcher = Files.isRegularFile(image.resolve("bin").resolve("lunavera")) || Files.isRegularFile(image.resolve("bin").resolve("lunavera.bat"));
        if (!launcher) {
            out.add("  MISSING  the launcher bin/lunavera (bin/lunavera.bat on Windows)");
            problems++;
        }

        // 4. the size
        long bytes;
        try (Stream<Path> files = Files.walk(image)) {
            bytes = files.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
        }
        out.add(String.format("Size of the folder: %.1f MB", bytes / 1_048_576.0));
        out.add(problems == 0 ? "OK: nothing wrong was found (the program itself was not started)." : "PROBLEMS: " + problems + " (see docs/PACKAGING.md, \"If the check lists an automatic module\").");
        return new Result(out, problems == 0 ? 0 : 1);
    }

    /** Runs the image's own java with the arguments and returns what it printed (both streams); gives up after a minute. */
    private static String run(Path javaExe, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(javaExe.toString());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] bytes = process.getInputStream().readAllBytes();
        if (!process.waitFor(60, TimeUnit.SECONDS)) process.destroyForcibly();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
