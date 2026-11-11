package com.coffeeshop.coffeeshopmanagement.tools;

import org.junit.Before;
import org.junit.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.Assert.*;

/**
 * TODO 6.15: {@code tools/ModuleCheck.java} says before a jlink build which jars jlink cannot use (automatic modules). The test builds
 * small jars of the three kinds (a real module, one with only an {@code Automatic-Module-Name} line, one with nothing), then runs the tool
 * the way a person does ({@code java tools/ModuleCheck.java <folder>}) and checks what it says and its exit code. It does not need
 * Maven, JavaFX or a database. The tool itself is what a real build on the shop's machine uses first; this test only shows that it tells
 * real from automatic modules, follows a module that requires an automatic one, and does not cry wolf.
 */
public class ModuleCheckTest {

    private Path work;
    private Path jars;

    @Before
    public void makeJars() throws Exception {
        work = Files.createTempDirectory("modulecheck");
        jars = Files.createDirectories(work.resolve("jars"));
        // 1. a real module: fake.real (has a module-info.class)
        Path realSrc = write(work.resolve("src-real/module-info.java"), "module fake.real { exports fake.real; }");
        write(work.resolve("src-real/fake/real/R.java"), "package fake.real; public class R {}");
        compile(work.resolve("out-real"), List.of(), realSrc, work.resolve("src-real/fake/real/R.java"));
        jar(jars.resolve("real-lib-1.0.jar"), work.resolve("out-real"), null);
        // 2. an automatic module with a name in its manifest: fake.auto (like the MariaDB driver)
        Path autoSrc = write(work.resolve("src-auto/fake/auto/A.java"), "package fake.auto; public class A {}");
        compile(work.resolve("out-auto"), List.of(), autoSrc);
        jar(jars.resolve("auto-lib-2.0.jar"), work.resolve("out-auto"), "fake.auto");
        // 3. a jar with nothing: only a name made from the file name ("plain-lib-3.0.jar" becomes "plain.lib")
        Path plainSrc = write(work.resolve("src-plain/fake/plain/P.java"), "package fake.plain; public class P {}");
        compile(work.resolve("out-plain"), List.of(), plainSrc);
        jar(jars.resolve("plain-lib-3.0.jar"), work.resolve("out-plain"), null);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static Path write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private static void compile(Path out, List<String> extra, Path... sources) throws IOException {
        Files.createDirectories(out);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("the tests need a JDK (a compiler)", compiler);
        List<String> args = new ArrayList<>(List.of("-d", out.toString(), "-proc:none", "-nowarn"));
        args.addAll(extra);
        for (Path s : sources) args.add(s.toString());
        assertEquals("compile " + args, 0, compiler.run(null, null, null, args.toArray(new String[0])));
    }

    private static void jar(Path target, Path classes, String automaticName) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (automaticName != null) manifest.getMainAttributes().putValue("Automatic-Module-Name", automaticName);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target), manifest);
             var files = Files.walk(classes)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                out.write(Files.readAllBytes(file));
                out.closeEntry();
            }
        }
    }

    private record Run(int exit, String output) {
    }

    private Run run(String... args) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> command = new ArrayList<>(List.of(java.toString(), Path.of("tools", "ModuleCheck.java").toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            in.transferTo(bytes);
        }
        return new Run(process.waitFor(), bytes.toString(StandardCharsets.UTF_8));
    }

    private Path moduleInfo(String body) throws IOException {
        return write(work.resolve("app/module-info.java"), "module the.app {\n" + body + "\n}\n");
    }

    // ------------------------------------------------------------------------------------------------ tests

    @Test
    public void aProgramThatNeedsOnlyRealModulesIsClear() throws Exception {
        Path info = moduleInfo("requires java.sql; requires fake.real;");
        Run run = run("--module-info", info.toString(), jars.toString());
        assertEquals(run.output(), 0, run.exit());
        assertTrue(run.output(), run.output().contains("OK: no automatic module is needed"));
    }

    @Test
    public void everyJarIsLabelledRealOrAutomaticWithItsModuleName() throws Exception {
        Run run = run("--module-info", moduleInfo("requires fake.real;").toString(), jars.toString());
        assertTrue(run.output(), run.output().matches("(?s).*REAL\\s+fake\\.real\\s+real-lib-1\\.0\\.jar.*"));
        assertTrue(run.output(), run.output().matches("(?s).*AUTOMATIC\\s+fake\\.auto\\s+auto-lib-2\\.0\\.jar.*"));
        assertTrue("a jar with nothing is automatic too, named from its file", run.output().matches("(?s).*AUTOMATIC\\s+plain\\.lib\\s+plain-lib-3\\.0\\.jar.*"));
    }

    @Test
    public void anAutomaticModuleThatTheProgramRequiresBlocksJlink() throws Exception {
        Run run = run("--module-info", moduleInfo("requires fake.real; requires fake.auto;").toString(), jars.toString());
        assertEquals(run.output(), 1, run.exit());
        assertTrue(run.output(), run.output().contains("BLOCKED"));
        assertTrue(run.output(), run.output().contains("fake.auto"));
        assertTrue(run.output(), run.output().contains("docs/PACKAGING.md"));
    }

    @Test
    public void aRealModuleThatNeedsAnAutomaticOneBlocksJlinkToo() throws Exception {
        // like sqlite-jdbc needing slf4j: the program asks only for fake.uses, which requires the automatic fake.auto
        Path src = write(work.resolve("src-uses/module-info.java"), "module fake.uses { requires fake.auto; exports fake.uses; }");
        Path cls = write(work.resolve("src-uses/fake/uses/U.java"), "package fake.uses; public class U { fake.auto.A a; }");
        compile(work.resolve("out-uses"), List.of("--module-path", jars.resolve("auto-lib-2.0.jar").toString()), src, cls);
        jar(jars.resolve("uses-lib-1.0.jar"), work.resolve("out-uses"), null);
        Run run = run("--module-info", moduleInfo("requires fake.uses;").toString(), jars.toString());
        assertEquals(run.output(), 1, run.exit());
        assertTrue(run.output(), run.output().contains("BLOCKED") && run.output().contains("fake.auto"));
    }

    @Test
    public void anAutomaticJarTheProgramDoesNotUseDoesNotBlock() throws Exception {
        Run run = run("--module-info", moduleInfo("requires fake.real;").toString(), jars.toString());
        assertEquals("fake.auto and plain.lib are in the folder but nobody requires them\n" + run.output(), 0, run.exit());
        assertFalse(run.output(), run.output().contains("BLOCKED"));
    }

    @Test
    public void commentsAndJdkModulesAreNotRequirements() throws Exception {
        Path info = moduleInfo("// requires fake.auto;\n/* requires fake.auto; */\nrequires transitive java.sql;\nrequires static java.desktop;\nrequires fake.real;");
        Run run = run("--module-info", info.toString(), jars.toString());
        assertEquals(run.output(), 0, run.exit());
        assertTrue(run.output(), run.output().contains("The program requires: [fake.real]"));
    }

    @Test
    public void aRequiredModuleThatIsNotAmongTheJarsIsReported() throws Exception {
        Run run = run("--module-info", moduleInfo("requires fake.real; requires not.there;").toString(), jars.toString());
        assertEquals(run.output(), 1, run.exit());
        assertTrue(run.output(), run.output().contains("could not be resolved") && run.output().contains("not.there"));
    }

    @Test
    public void withoutAModuleInfoOnlyTheJarsAreLookedAtAndAnAutomaticOneStillCounts() throws Exception {
        Run run = run("--module-info", work.resolve("no/such/module-info.java").toString(), jars.toString());
        assertEquals(run.output(), 1, run.exit());
        assertTrue(run.output(), run.output().contains("only the jars were looked at"));
        Path onlyReal = Files.createDirectories(work.resolve("onlyreal"));
        Files.copy(jars.resolve("real-lib-1.0.jar"), onlyReal.resolve("real-lib-1.0.jar"));
        Run clear = run("--module-info", work.resolve("no/such/module-info.java").toString(), onlyReal.toString());
        assertEquals(clear.output(), 0, clear.exit());
        assertTrue(clear.output(), clear.output().contains("No automatic module among the jars"));
    }

    @Test
    public void oneJarCanBeGivenInsteadOfAFolder() throws Exception {
        Run run = run("--module-info", moduleInfo("requires fake.real;").toString(), jars.resolve("real-lib-1.0.jar").toString());
        assertEquals(run.output(), 0, run.exit());
        assertTrue(run.output(), run.output().contains("Jars (1):"));
    }

    @Test
    public void wrongUseIsRefusedWithAnExplanation() throws Exception {
        Run none = run();
        assertEquals(none.output(), 2, none.exit());
        assertTrue(none.output(), none.output().contains("Usage:"));
        Run missing = run(work.resolve("nope").toString());
        assertEquals(missing.output(), 2, missing.exit());
        assertTrue(missing.output(), missing.output().contains("Not found"));
        Path empty = Files.createDirectories(work.resolve("empty"));
        Run noJars = run(empty.toString());
        assertEquals(noJars.output(), 2, noJars.exit());
        assertTrue(noJars.output(), noJars.output().contains("No jar files"));
    }

    @Test
    public void theProjectsOwnModuleInfoIsReadWithoutTrouble() throws Exception {
        // the real file of this project: its requires lines are found (JavaFX, the two drivers, slf4j), java.* and jdk.* left out
        Run run = run("--module-info", Path.of("src", "main", "java", "module-info.java").toString(), jars.toString());
        assertEquals("none of those modules is among the test jars", 1, run.exit());
        assertTrue(run.output(), run.output().contains("could not be resolved"));
        assertTrue(run.output(), run.output().contains("javafx.controls") || run.output().contains("org.mariadb.jdbc")
                || run.output().contains("org.xerial.sqlitejdbc") || run.output().contains("org.slf4j"));
    }
}
