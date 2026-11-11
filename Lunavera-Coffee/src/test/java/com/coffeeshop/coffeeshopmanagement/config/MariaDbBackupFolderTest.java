package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * TODO 3.70: {@code mysqldump.exe} reads its arguments in the Windows ANSI code page, so a path with Vietnamese letters given to it
 * as an argument can fail. These tests run a stand-in {@code mysqldump} (a shell script, so not on Windows) in folders and with file
 * names that have such letters and look at what the program really receives: only plain ASCII arguments, started inside the
 * backup's folder, and the finished file under the real accented name with nothing left beside it. The real Windows check
 * (a Windows account whose name has Vietnamese letters) is in section 0 of the TODO list.
 */
public class MariaDbBackupFolderTest {

    private static final String ACCENTED_FOLDER = "Quán Cà Phê dữ liệu";
    private static final String ACCENTED_FILE = "Sao lưu ngày hôm nay.sql";

    private static DatabaseSettings settings(String... kv) {
        Properties p = new Properties();
        p.setProperty("db.type", "mariadb");
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return DatabaseSettings.fromProperties(p, Map.<String, String>of()::get);
    }

    private static void needsShellAndAccents(Path base) {
        Assume.assumeTrue("needs a Unix shell for the stand-in program",
                !System.getProperty("os.name").toLowerCase().contains("win") && new java.io.File("/bin/sh").exists());
        try {
            Path probe = Files.createDirectories(base.resolve(ACCENTED_FOLDER));
            Files.delete(probe);
        } catch (InvalidPathException | IOException cannotNameIt) {
            Assume.assumeTrue("this JVM cannot make a file name with Vietnamese letters (set the locale to UTF-8)", false);
        }
    }

    /**
     * A stand-in mysqldump: for {@code --version} it says it is MySQL's; otherwise it writes what it saw (working folder, every
     * argument, and the content of a certificate file named in --ssl-ca) into {@code seen.txt} in its working folder, and the dump
     * into the file named by --result-file, as the real one does.
     */
    private static Path fake(String extra, int exitCode) throws IOException {
        Path script = Files.createTempFile("fake-mysqldump", ".sh");
        String body = "#!/bin/sh\n"
                + "if [ \"$1\" = \"--version\" ]; then echo 'mysqldump  Ver 8.0.36 for Linux (MySQL Community Server - GPL)'; exit 0; fi\n"
                + "out=\"\"; ca=\"\"\n"
                + "{ echo \"cwd=$(pwd)\"; for a in \"$@\"; do echo \"arg=$a\"; done; } > seen.txt\n"
                + "for a in \"$@\"; do\n"
                + "  case \"$a\" in\n"
                + "    --result-file=*) out=\"${a#--result-file=}\" ;;\n"
                + "    --ssl-ca=*) ca=\"${a#--ssl-ca=}\" ;;\n"
                + "  esac\n"
                + "done\n"
                + "if [ -n \"$ca\" ]; then echo \"ca=$(cat \"$ca\")\" >> seen.txt; fi\n"
                + extra + "\n"
                + (exitCode == 0 ? "echo \"-- dump line\" > \"$out\"\nexit 0\n" : "echo \"mysqldump: Got error: 1045: Access denied\"; exit " + exitCode + "\n");
        Files.writeString(script, body);
        script.toFile().setExecutable(true);
        return script;
    }

    private static List<String> seen(Path folder) throws IOException {
        return Files.readAllLines(folder.resolve("seen.txt"), StandardCharsets.UTF_8);
    }

    private static boolean plain(String text) {
        return MariaDbBackup.isPlainAscii(text);
    }

    private static List<String> names(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(f -> f.getFileName().toString()).sorted().toList();
        }
    }

    // ---- the pure parts

    @Test
    public void onlyPlainAsciiCountsAsSafeForAnArgument() {
        assertTrue(plain("C:\\xampp\\mysql\\bin\\ca-cert.pem"));
        assertTrue(plain("/home/shop/Sao luu/b.sql"));
        assertTrue(plain(null));
        assertTrue(plain(""));
        assertFalse(plain("C:\\Users\\Nguyễn Văn An\\ca.pem"));
        assertFalse(plain("Sao lưu"));
        assertFalse(plain("tab\there"));
        assertFalse(plain("café"));
    }

    @Test
    public void aBareProgramNameIsLeftAloneAndARelativePathWithFoldersBecomesAbsolute() {
        assertEquals("mysqldump", MariaDbBackup.programFor("mysqldump"));
        assertEquals("mysqldump.exe", MariaDbBackup.programFor("mysqldump.exe"));
        String relative = MariaDbBackup.programFor("tools/mysqldump");
        assertTrue(relative, Path.of(relative).isAbsolute() && relative.endsWith("mysqldump"));
        String absolute = Path.of("/opt/mysql/bin/mysqldump").toAbsolutePath().toString();
        assertEquals(absolute, MariaDbBackup.programFor(absolute));
    }

    @Test
    public void theCommandCarriesTheResultFileAndTheCertificateExactlyAsGiven() throws Exception {
        Path cert = Files.createTempFile("ignored", ".pem");
        DatabaseSettings s = settings("db.sslMode", "verify-ca", "db.sslServerCert", cert.toString());
        List<String> command = MariaDbBackup.command(s, Path.of("lunavera-dump-1.part"), MariaDbBackup.Flavour.MYSQL, "lunavera-ca-1.pem");
        assertTrue(command.toString(), command.contains("--result-file=lunavera-dump-1.part"));
        assertTrue(command.toString(), command.contains("--ssl-ca=lunavera-ca-1.pem"));
        assertFalse(command.toString(), command.stream().anyMatch(a -> a.contains(cert.getFileName().toString())));
    }

    // ---- a real run, in folders with Vietnamese letters

    @Test
    public void inAFolderWithVietnameseLettersTheProgramReceivesOnlyPlainArgumentsAndTheFileEndsUnderItsRealName() throws Exception {
        Path base = Files.createTempDirectory("dump-base");
        needsShellAndAccents(base);
        Path folder = Files.createDirectories(base.resolve(ACCENTED_FOLDER));
        Path target = folder.resolve(ACCENTED_FILE); // a name typed by the user, with accents and spaces
        MariaDbBackup.dumpTo(settings("db.mysqldump", fake("", 0).toString()), target);

        assertEquals("-- dump line", Files.readString(target, StandardCharsets.UTF_8).trim());
        List<String> seen = seen(folder);
        assertTrue(seen.toString(), seen.get(0).startsWith("cwd=") && seen.get(0).endsWith(ACCENTED_FOLDER));
        for (String line : seen.subList(1, seen.size())) {
            assertTrue("every argument is plain ASCII: " + line, plain(line));
        }
        assertTrue(seen.toString(), seen.stream().anyMatch(l -> l.startsWith("arg=--result-file=lunavera-dump-") && l.endsWith(".part")));
        assertEquals("nothing left beside the backup but the stand-in's own note", List.of(ACCENTED_FILE, "seen.txt"), names(folder));
    }

    @Test
    public void aBackupFileNameWithAccentsAndSpacesInAPlainFolderWorksToo() throws Exception {
        Path base = Files.createTempDirectory("dump-plain");
        needsShellAndAccents(base);
        Path target = base.resolve(ACCENTED_FILE);
        MariaDbBackup.dumpTo(settings("db.mysqldump", fake("", 0).toString()), target);
        assertTrue(Files.isRegularFile(target));
        for (String line : seen(base).subList(1, seen(base).size())) assertTrue(line, plain(line));
    }

    @Test
    public void aFailedDumpLeavesNothingBehindAndAnOlderFileOfThatNameIsKept() throws Exception {
        Path base = Files.createTempDirectory("dump-fail");
        needsShellAndAccents(base);
        Path folder = Files.createDirectories(base.resolve(ACCENTED_FOLDER));
        Path target = folder.resolve(ACCENTED_FILE);
        Files.writeString(target, "the earlier backup", StandardCharsets.UTF_8);
        // this one writes a half file and then fails
        Path script = fake("echo 'half' > \"$out\"", 2);
        try {
            MariaDbBackup.dumpTo(settings("db.mysqldump", script.toString()), target);
            fail("a failing mysqldump must be reported");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Access denied"));
        }
        assertEquals("the earlier backup is untouched", "the earlier backup", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals(List.of(ACCENTED_FILE, "seen.txt"), names(folder));
    }

    @Test(timeout = 30_000)
    public void aDumpThatRunsTooLongIsStoppedAndLeavesNoPartialFile() throws Exception {
        Path base = Files.createTempDirectory("dump-slow");
        needsShellAndAccents(base);
        Path folder = Files.createDirectories(base.resolve(ACCENTED_FOLDER));
        Path script = fake("echo 'partial' > \"$out\"; sleep 20", 0);
        try {
            MariaDbBackup.dumpTo(settings("db.mysqldump", script.toString()), folder.resolve(ACCENTED_FILE), 1);
            fail("a program still running at the limit must be stopped");
        } catch (IOException expected) {
            assertFalse(expected.getMessage().isBlank());
        }
        assertEquals(List.of("seen.txt"), names(folder));
    }

    @Test
    public void aCertificateWhoseFolderHasAccentsIsCopiedUnderAPlainNameForTheRunAndRemoved() throws Exception {
        Path base = Files.createTempDirectory("dump-cert");
        needsShellAndAccents(base);
        Path certFolder = Files.createDirectories(base.resolve("Chứng chỉ"));
        Path cert = certFolder.resolve("máy chủ.pem");
        Files.writeString(cert, "CERT-BYTES", StandardCharsets.UTF_8);
        Path folder = Files.createDirectories(base.resolve(ACCENTED_FOLDER));
        DatabaseSettings s = settings("db.mysqldump", fake("", 0).toString(), "db.sslMode", "verify-ca", "db.sslServerCert", cert.toString());
        MariaDbBackup.dumpTo(s, folder.resolve(ACCENTED_FILE));

        List<String> seen = seen(folder);
        for (String line : seen.subList(1, seen.size())) {
            if (line.startsWith("ca=")) continue; // what the program read from the file, not an argument
            assertTrue("every argument is plain ASCII: " + line, plain(line));
        }
        assertTrue(seen.toString(), seen.stream().anyMatch(l -> l.matches("arg=--ssl-ca=lunavera-ca-[0-9a-f]+\\.pem")));
        assertTrue("the program could read the certificate through the plain name", seen.contains("ca=CERT-BYTES"));
        assertEquals("the copy is removed", List.of(ACCENTED_FILE, "seen.txt"), names(folder));
        assertEquals("the original is untouched", "CERT-BYTES", Files.readString(cert, StandardCharsets.UTF_8));
    }

    @Test
    public void aCertificateWithAPlainPathIsPassedAsItIsAndNotCopied() throws Exception {
        Path base = Files.createTempDirectory("dump-cert-plain");
        needsShellAndAccents(base);
        Path cert = base.resolve("ca.pem");
        Files.writeString(cert, "PLAIN-CERT", StandardCharsets.UTF_8);
        Path folder = Files.createDirectories(base.resolve(ACCENTED_FOLDER));
        DatabaseSettings s = settings("db.mysqldump", fake("", 0).toString(), "db.sslMode", "verify-ca", "db.sslServerCert", cert.toString());
        MariaDbBackup.dumpTo(s, folder.resolve(ACCENTED_FILE));
        assertTrue(seen(folder).toString(), seen(folder).contains("arg=--ssl-ca=" + cert));
    }

    @Test
    public void aProgramThatSaysItIsDoneButWritesNothingIsReportedNotTrusted() throws Exception {
        Path base = Files.createTempDirectory("dump-nofile");
        needsShellAndAccents(base);
        Path folder = Files.createDirectories(base.resolve(ACCENTED_FOLDER));
        Path script = fake("rm -f \"$out\"", 0);
        Path target = folder.resolve(ACCENTED_FILE);
        // the stand-in writes the file after the extra line, so make it exit before doing so
        Path quiet = Files.createTempFile("fake-quiet", ".sh");
        Files.writeString(quiet, "#!/bin/sh\nexit 0\n");
        quiet.toFile().setExecutable(true);
        try {
            MariaDbBackup.dumpTo(settings("db.mysqldump", quiet.toString()), target);
            fail("no file was written");
        } catch (IOException expected) {
            assertFalse(expected.getMessage().isBlank());
        }
        assertFalse(Files.exists(target));
        assertEquals(List.of(), names(folder));
        Files.deleteIfExists(script);
    }

    @Test
    public void aFolderThatDoesNotExistYetIsCreated() throws Exception {
        Path base = Files.createTempDirectory("dump-new");
        needsShellAndAccents(base);
        Path target = base.resolve("Thư mục mới").resolve("Sao lưu").resolve(ACCENTED_FILE);
        MariaDbBackup.dumpTo(settings("db.mysqldump", fake("", 0).toString()), target);
        assertTrue(Files.isRegularFile(target));
    }
}
