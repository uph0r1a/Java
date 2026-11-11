import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
import java.lang.module.ResolvedModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * TODO 6.15: tells in seconds, BEFORE a jlink build, which dependency jars jlink cannot use. jlink only links real modules (a jar with a
 * {@code module-info.class}); a jar that is only an "automatic module" (it has just an {@code Automatic-Module-Name} line in its manifest,
 * or nothing) stops the build with "automatic module cannot be used with jlink". The 2026-11-03 hand-made image was built without the
 * MariaDB driver, so this was never seen for it.
 *
 * <p>Run it with the JDK only, from the project folder:
 * <pre>
 *   ./mvnw dependency:copy-dependencies -DoutputDirectory=target/deps
 *   java tools/ModuleCheck.java target/deps
 * </pre>
 * It reads the {@code requires} lines of {@code src/main/java/module-info.java} (or the file given with {@code --module-info}), resolves
 * them over the jars the way the module system does (the running JDK is the parent), and lists every jar in that result that is an
 * automatic module. Exit code 0: nothing in the way; 1: at least one jar blocks jlink, or a required module was not found; 2: wrong use.
 *
 * <p>This is a plain single-file program (no Maven, no library), so it also runs on the shop's Windows machine.
 */
public final class ModuleCheck {

    /** Not tied to the start of a line: two directives on one line must both be seen (a missed one would give a false "OK"). */
    private static final Pattern REQUIRES = Pattern.compile("(?<![\\w.])requires\\s+(?:transitive\\s+|static\\s+)*([\\w.]+)\\s*;");

    private ModuleCheck() {
    }

    /** What the check found; {@link #exitCode()} is what the program returns. */
    record Result(List<String> lines, int exitCode) {
    }

    public static void main(String[] args) throws IOException {
        List<Path> inputs = new ArrayList<>();
        Path moduleInfo = Path.of("src", "main", "java", "module-info.java");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--module-info") && i + 1 < args.length) {
                moduleInfo = Path.of(args[++i]);
            } else {
                inputs.add(Path.of(args[i]));
            }
        }
        if (inputs.isEmpty()) {
            System.err.println("Usage: java tools/ModuleCheck.java [--module-info path/module-info.java] <folder-of-jars | jar>...");
            System.exit(2);
        }
        Result result = check(inputs, moduleInfo);
        result.lines().forEach(System.out::println);
        System.exit(result.exitCode());
    }

    static Result check(List<Path> inputs, Path moduleInfo) throws IOException {
        List<String> out = new ArrayList<>();
        List<Path> jars = new ArrayList<>();
        for (Path input : inputs) {
            if (Files.isDirectory(input)) {
                try (Stream<Path> files = Files.list(input)) {
                    files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().forEach(jars::add);
                }
            } else if (Files.isRegularFile(input)) {
                jars.add(input);
            } else {
                out.add("Not found: " + input);
                return new Result(out, 2);
            }
        }
        if (jars.isEmpty()) {
            out.add("No jar files found in the given folder(s).");
            return new Result(out, 2);
        }

        // 1. every jar on its own: real module, automatic module, or unusable
        Map<String, ModuleReference> byName = new TreeMap<>();
        Set<String> automatic = new TreeSet<>();
        out.add("Jars (" + jars.size() + "):");
        for (Path jar : jars) {
            String file = jar.getFileName().toString();
            try {
                Set<ModuleReference> found = ModuleFinder.of(jar).findAll();
                if (found.isEmpty()) {
                    out.add(String.format("  %-10s %-32s %s", "NO-MODULE", "-", file));
                    continue;
                }
                for (ModuleReference reference : found) {
                    ModuleDescriptor d = reference.descriptor();
                    byName.putIfAbsent(d.name(), reference);
                    if (d.isAutomatic()) automatic.add(d.name());
                    out.add(String.format("  %-10s %-32s %s", d.isAutomatic() ? "AUTOMATIC" : "REAL", d.name(), file));
                }
            } catch (FindException e) {
                out.add(String.format("  %-10s %-32s %s  (%s)", "UNUSABLE", "-", file, e.getMessage()));
            }
        }

        // 2. what the program asks for, resolved over the jars on top of the running JDK
        List<String> roots = rootsOf(moduleInfo, out);
        if (roots == null) {
            out.add(automatic.isEmpty() ? "No automatic module among the jars." : "Automatic modules among the jars (they block jlink if the program uses them): " + automatic);
            return new Result(out, automatic.isEmpty() ? 0 : 1);
        }
        ModuleFinder finder = ModuleFinder.of(jars.toArray(new Path[0]));
        List<String> blockers = new ArrayList<>();
        try {
            Configuration configuration = Configuration.resolve(finder, List.of(ModuleLayer.boot().configuration()), ModuleFinder.of(), roots);
            for (ResolvedModule module : configuration.modules()) {
                if (module.reference().descriptor().isAutomatic()) blockers.add(module.name());
            }
        } catch (ResolutionException | FindException e) {
            out.add("");
            out.add("A module the program requires could not be resolved from these jars: " + e.getMessage());
            out.add("(Is a jar missing from the folder? Did the folder come from dependency:copy-dependencies?)");
            return new Result(out, 1);
        }
        out.add("");
        out.add("The program requires: " + roots);
        if (blockers.isEmpty()) {
            out.add("OK: no automatic module is needed, so jlink can link what the program requires.");
            return new Result(out, 0);
        }
        out.add("BLOCKED: jlink cannot use these automatic modules: " + new TreeSet<>(blockers));
        out.add("See \"If the check lists an automatic module\" in docs/PACKAGING.md.");
        return new Result(out, 1);
    }

    /** The non-JDK modules the program requires (JavaFX and drivers), or null when there is no module-info to read. */
    static List<String> rootsOf(Path moduleInfo, List<String> out) throws IOException {
        if (!Files.isRegularFile(moduleInfo)) {
            out.add("");
            out.add("No module-info.java at " + moduleInfo + " (run from the project folder or give --module-info): only the jars were looked at.");
            return null;
        }
        String text = Files.readString(moduleInfo).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        List<String> roots = new ArrayList<>();
        Matcher m = REQUIRES.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            if (!name.startsWith("java.") && !name.startsWith("jdk.")) roots.add(name);
        }
        return roots;
    }
}
