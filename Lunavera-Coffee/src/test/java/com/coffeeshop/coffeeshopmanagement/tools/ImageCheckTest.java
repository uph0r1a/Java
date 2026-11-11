package com.coffeeshop.coffeeshopmanagement.tools;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * TODO 6.15: {@code tools/ImageCheck.java} reads a finished program folder through its own {@code bin/java}. The test makes a small fake folder
 * whose {@code bin/java} is a shell script that prints prepared answers, then runs the tool the way a person does and checks what it says and
 * its exit code. It proves the tool's decisions (a missing module, a driver that is not provided, no launcher, not a program folder), NOT that
 * a real image is right: that is what the tool is for, on the real build. Skipped on Windows (a script cannot stand in for java.exe).
 */
public class ImageCheckTest {

    private static final String MODULES = String.join("\n", "java.base@21", "java.desktop@21", "java.sql@21", "javafx.base@21.0.6", "javafx.controls@21.0.6",
            "javafx.fxml@21.0.6", "javafx.graphics@21.0.6", "javafx.media@21.0.6", "org.mariadb.jdbc@3.4.1", "org.slf4j@2.0.16",
            "org.xerial.sqlitejdbc@3.46.1.3", "com.coffeeshop.coffeeshopmanagement", "");
    private static final String MARIA = "org.mariadb.jdbc@3.4.1\nexports org.mariadb.jdbc\nuses org.mariadb.jdbc.plugin.Codec\nprovides java.sql.Driver with org.mariadb.jdbc.Driver\n";
    private static final String SQLITE = "org.xerial.sqlitejdbc@3.46.1.3\nexports org.sqlite\nprovides java.sql.Driver with org.sqlite.JDBC\n";

    private Path image;

    @Before
    public void makeAFakeImage() throws Exception {
        Assume.assumeFalse("a shell script cannot stand in for java.exe", System.getProperty("os.name", "").toLowerCase().contains("win"));
        image = Files.createTempDirectory("imagecheck").resolve("lunavera");
        Path bin = Files.createDirectories(image.resolve("bin"));
        Path java = bin.resolve("java");
        Files.writeString(java, "#!/bin/sh\nd=\"$(dirname \"$0\")\"\ncase \"$1\" in\n  --list-modules) cat \"$d/modules.txt\";;\n"
                + "  --describe-module) cat \"$d/describe-$2.txt\" 2>/dev/null || { echo \"Error: module $2 not found\"; exit 1; };;\nesac\n");
        assertTrue(java.toFile().setExecutable(true));
        Files.writeString(bin.resolve("modules.txt"), MODULES);
        Files.writeString(bin.resolve("describe-org.mariadb.jdbc.txt"), MARIA);
        Files.writeString(bin.resolve("describe-org.xerial.sqlitejdbc.txt"), SQLITE);
        Files.writeString(bin.resolve("lunavera"), "#!/bin/sh\n");
    }

    private record Run(int exit, String output) {
    }

    private Run run(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                Path.of("tools", "ImageCheck.java").toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            in.transferTo(bytes);
        }
        return new Run(process.waitFor(), bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void aCompleteImageIsClear() throws Exception {
        Run run = run(image.toString());
        assertEquals(run.output(), 0, run.exit());
        assertTrue(run.output(), run.output().contains("OK: nothing wrong was found"));
        assertTrue(run.output(), run.output().contains("driver ok  org.mariadb.jdbc") && run.output().contains("driver ok  org.xerial.sqlitejdbc"));
        assertTrue(run.output(), run.output().contains("Size of the folder:"));
        assertFalse(run.output(), run.output().contains("WARNING"));
    }

    @Test
    public void aMissingModuleIsNamed() throws Exception {
        Files.writeString(image.resolve("bin/modules.txt"), MODULES.replace("org.mariadb.jdbc@3.4.1\n", "").replace("javafx.media@21.0.6\n", ""));
        Run run = run(image.toString());
        assertEquals(run.output(), 1, run.exit());
        assertTrue(run.output(), run.output().contains("MISSING  org.mariadb.jdbc"));
        assertTrue(run.output(), run.output().contains("MISSING  javafx.media"));
        assertFalse("a module that is there is not reported", run.output().contains("MISSING  org.slf4j"));
    }

    @Test
    public void aDriverThatIsNotProvidedIsAProblem() throws Exception {
        Files.writeString(image.resolve("bin/describe-org.mariadb.jdbc.txt"), "org.mariadb.jdbc@3.4.1\nexports org.mariadb.jdbc\nuses org.mariadb.jdbc.plugin.Codec\n");
        Run run = run(image.toString());
        assertEquals(run.output(), 1, run.exit());
        assertTrue(run.output(), run.output().contains("NO DRIVER  org.mariadb.jdbc") && run.output().contains("No suitable driver"));
        assertTrue("the other driver is fine", run.output().contains("driver ok  org.xerial.sqlitejdbc"));
    }

    @Test
    public void aDriverWithNoUsesLineIsOnlyAWarning() throws Exception {
        Files.writeString(image.resolve("bin/describe-org.mariadb.jdbc.txt"), "org.mariadb.jdbc@3.4.1\nexports org.mariadb.jdbc\nprovides java.sql.Driver with org.mariadb.jdbc.Driver\n");
        Run run = run(image.toString());
        assertEquals(run.output(), 0, run.exit());
        assertTrue(run.output(), run.output().contains("WARNING") && run.output().contains("ServiceLoader"));
    }

    @Test
    public void aMissingLauncherIsAProblem() throws Exception {
        Files.delete(image.resolve("bin/lunavera"));
        Run run = run(image.toString());
        assertEquals(run.output(), 1, run.exit());
        assertTrue(run.output(), run.output().contains("MISSING  the launcher"));
    }

    @Test
    public void theWindowsLauncherCountsToo() throws Exception {
        Files.delete(image.resolve("bin/lunavera"));
        Files.writeString(image.resolve("bin/lunavera.bat"), "@echo off\n");
        Run run = run(image.toString());
        assertEquals(run.output(), 0, run.exit());
    }

    @Test
    public void aFolderThatIsNotAnImageIsRefused() throws Exception {
        Path empty = Files.createTempDirectory("notanimage");
        Run run = run(empty.toString());
        assertEquals(run.output(), 2, run.exit());
        assertTrue(run.output(), run.output().contains("Not a program folder") && run.output().contains("-Pmodular-image"));
    }

    @Test
    public void tooManyArgumentsAreRefused() throws Exception {
        Run run = run("a", "b");
        assertEquals(run.output(), 2, run.exit());
        assertTrue(run.output(), run.output().contains("Usage:"));
    }
}
