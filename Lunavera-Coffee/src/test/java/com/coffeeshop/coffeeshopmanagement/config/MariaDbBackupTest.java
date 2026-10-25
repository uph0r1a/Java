package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MariaDbBackupTest {

    private static DatabaseSettings settings(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return DatabaseSettings.fromProperties(p, Map.<String, String>of()::get);
    }

    @Test
    public void theDumpCommandNamesTheServerTheUserAndTheDatabase() {
        DatabaseSettings s = settings("db.type", "mariadb", "db.host", "10.0.0.5", "db.port", "3307",
                "db.name", "quan", "db.user", "thungan", "db.mysqldump", "C:\\xampp\\mysql\\bin\\mysqldump.exe");
        List<String> command = MariaDbBackup.command(s, Path.of("backup.sql"));
        assertEquals("C:\\xampp\\mysql\\bin\\mysqldump.exe", command.get(0));
        assertTrue(command.contains("--host=10.0.0.5"));
        assertTrue(command.contains("--port=3307"));
        assertTrue(command.contains("--user=thungan"));
        assertTrue(command.contains("--single-transaction"));
        assertTrue(command.contains("--default-character-set=utf8mb4"));
        assertEquals("the database name comes last", "quan", command.get(command.size() - 1));
    }

    @Test
    public void theResultFileIsAbsoluteAndThePasswordNeverOnTheCommandLine() {
        DatabaseSettings s = settings("db.type", "mariadb", "db.password", "topsecret");
        List<String> command = MariaDbBackup.command(s, Path.of("out", "b.sql"));
        String resultFile = command.stream().filter(a -> a.startsWith("--result-file=")).findFirst().orElseThrow();
        assertTrue(resultFile, Path.of(resultFile.substring("--result-file=".length())).isAbsolute());
        for (String argument : command) {
            assertFalse("the password must travel in MYSQL_PWD, not here: " + argument, argument.contains("topsecret"));
        }
    }

    // ---- TODO 2.74: --no-tablespaces, with a fall-back for a client that does not know it ----

    @Test
    public void theDumpAsksNotToReadTablespaceInformation() {
        java.util.List<String> command = MariaDbBackup.command(settings(), java.nio.file.Path.of("b.sql"));
        org.junit.Assert.assertTrue(command.toString(), command.contains("--no-tablespaces"));
        org.junit.Assert.assertTrue("it comes before the database name, which is last", command.indexOf("--no-tablespaces") < command.size() - 1);
    }

    @Test
    public void onlyARefusalOfThatOptionTriggersTheRetry() {
        org.junit.Assert.assertTrue(MariaDbBackup.rejectedNoTablespaces("mysqldump: unknown option '--no-tablespaces'"));
        org.junit.Assert.assertTrue(MariaDbBackup.rejectedNoTablespaces("mysqldump: [ERROR] unknown variable 'no-tablespaces'"));
        org.junit.Assert.assertTrue(MariaDbBackup.rejectedNoTablespaces("mysqldump: unrecognized option '--no-tablespaces'"));
        org.junit.Assert.assertFalse(MariaDbBackup.rejectedNoTablespaces("mysqldump: Got error: 1045: Access denied for user"));
        org.junit.Assert.assertFalse("a different unknown option is not ours", MariaDbBackup.rejectedNoTablespaces("mysqldump: unknown option '--foo'"));
        org.junit.Assert.assertFalse(MariaDbBackup.rejectedNoTablespaces(null));
        org.junit.Assert.assertFalse(MariaDbBackup.rejectedNoTablespaces(""));
    }

    /** A stand-in for mysqldump (a shell script): refuses --no-tablespaces like an old client, otherwise writes the result file. */
    private static java.nio.file.Path fakeDump(boolean knowsOption, boolean alwaysFails) throws java.io.IOException {
        java.nio.file.Path script = java.nio.file.Files.createTempFile("fake-mysqldump", ".sh");
        String body = "#!/bin/sh\n"
                + "out=\"\"\n"
                + "for a in \"$@\"; do\n"
                + "  case \"$a\" in\n"
                + "    --result-file=*) out=\"${a#--result-file=}\" ;;\n"
                + (knowsOption ? "" : "    --no-tablespaces) echo \"mysqldump: unknown option '--no-tablespaces'\"; exit 2 ;;\n")
                + "  esac\n"
                + "done\n"
                + (alwaysFails ? "echo \"mysqldump: Got error: 1045: Access denied\"; exit 2\n" : "echo \"-- dump of $*\" > \"$out\"\nexit 0\n");
        java.nio.file.Files.writeString(script, body);
        script.toFile().setExecutable(true);
        return script;
    }

    private static boolean unixShell() {
        return !System.getProperty("os.name").toLowerCase().contains("win") && new java.io.File("/bin/sh").exists();
    }

    @Test
    public void anOldClientThatRefusesTheOptionIsRetriedWithoutIt() throws Exception {
        org.junit.Assume.assumeTrue("needs a Unix shell for the stand-in program", unixShell());
        java.nio.file.Path script = fakeDump(false, false);
        java.nio.file.Path target = java.nio.file.Files.createTempDirectory("dump").resolve("b.sql");
        MariaDbBackup.dumpTo(settings("db.mysqldump", script.toString()), target);
        String written = java.nio.file.Files.readString(target);
        org.junit.Assert.assertTrue("the file was written by the second run", written.startsWith("-- dump of"));
        org.junit.Assert.assertFalse("and that run did not carry the option: " + written, written.contains("--no-tablespaces"));
    }

    @Test
    public void aClientThatKnowsTheOptionIsRunOnceWithIt() throws Exception {
        org.junit.Assume.assumeTrue("needs a Unix shell for the stand-in program", unixShell());
        java.nio.file.Path script = fakeDump(true, false);
        java.nio.file.Path target = java.nio.file.Files.createTempDirectory("dump").resolve("b.sql");
        MariaDbBackup.dumpTo(settings("db.mysqldump", script.toString()), target);
        org.junit.Assert.assertTrue(java.nio.file.Files.readString(target).contains("--no-tablespaces"));
    }

    @Test
    public void anotherFailureIsNotRetriedAndLeavesNoFileBehind() throws Exception {
        org.junit.Assume.assumeTrue("needs a Unix shell for the stand-in program", unixShell());
        java.nio.file.Path script = fakeDump(true, true);
        java.nio.file.Path target = java.nio.file.Files.createTempDirectory("dump").resolve("b.sql");
        try {
            MariaDbBackup.dumpTo(settings("db.mysqldump", script.toString()), target);
            org.junit.Assert.fail("a failing mysqldump must be reported");
        } catch (java.io.IOException expected) {
            org.junit.Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("Access denied"));
        }
        org.junit.Assert.assertFalse(java.nio.file.Files.exists(target));
    }

    // --- the time limit really applies (TODO 2.73); these use a small shell script as the fake mysqldump, so not on Windows ---

    private static DatabaseSettings fake(java.nio.file.Path script) {
        return settings("db.type", "mariadb", "db.mysqldump", script.toString());
    }

    private static java.nio.file.Path script(String body) throws java.io.IOException {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("fake-mysqldump", ".sh");
        java.nio.file.Files.writeString(file, "#!/bin/sh\n" + body + "\n");
        file.toFile().setExecutable(true);
        return file;
    }

    @Test(timeout = 20_000)
    public void aDumpThatNeverEndsIsStoppedAtTheLimit() throws Exception {
        org.junit.Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
        java.nio.file.Path target = java.nio.file.Files.createTempFile("dump", ".sql");
        long start = System.nanoTime();
        try {
            MariaDbBackup.dumpTo(fake(script("echo starting; sleep 60")), target, 1);
            org.junit.Assert.fail("a hanging dump must be stopped");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("quá 1 giây"));
        }
        assertTrue("it returned at the limit, not after the program", (System.nanoTime() - start) / 1_000_000_000L < 15);
        assertFalse("no partial file is left", java.nio.file.Files.exists(target));
    }

    @Test(timeout = 20_000)
    public void aFailingDumpReportsItsMessageAndAGoodOneSucceeds() throws Exception {
        org.junit.Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
        java.nio.file.Path target = java.nio.file.Files.createTempFile("dump", ".sql");
        try {
            MariaDbBackup.dumpTo(fake(script("echo 'Got error: 1045 Access denied'; exit 2")), target, 10);
            org.junit.Assert.fail("a non-zero exit is an error");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("mã 2") && expected.getMessage().contains("Access denied"));
        }
        MariaDbBackup.dumpTo(fake(script("echo done")), target, 10); // no exception
    }

    // ---- TODO 2A.42: the TLS settings of the connection also go to mysqldump ----

    private static final String MARIADB_VERSION = "mysqldump  Ver 10.19 Distrib 10.11.6-MariaDB, for Win64 (AMD64)";
    private static final String MARIADB_NEW_VERSION = "mariadb-dump from 11.4.2-MariaDB, client 10.19 for linux-systemd (x86_64)";
    private static final String MYSQL_VERSION = "mysqldump  Ver 8.0.36 for Linux on x86_64 (MySQL Community Server - GPL)";

    @Test
    public void theTwoFamiliesOfMysqldumpAreToldApart() {
        assertEquals(MariaDbBackup.Flavour.MARIADB, MariaDbBackup.flavourOf(MARIADB_VERSION));
        assertEquals(MariaDbBackup.Flavour.MARIADB, MariaDbBackup.flavourOf(MARIADB_NEW_VERSION));
        assertEquals(MariaDbBackup.Flavour.MYSQL, MariaDbBackup.flavourOf(MYSQL_VERSION));
        assertEquals(MariaDbBackup.Flavour.UNKNOWN, MariaDbBackup.flavourOf("something else 1.0"));
        assertEquals(MariaDbBackup.Flavour.UNKNOWN, MariaDbBackup.flavourOf(""));
        assertEquals(MariaDbBackup.Flavour.UNKNOWN, MariaDbBackup.flavourOf(null));
    }

    @Test
    public void anUnencryptedConnectionAddsNoTlsOptionWhateverTheProgram() {
        DatabaseSettings s = settings("db.type", "mariadb");
        for (MariaDbBackup.Flavour f : MariaDbBackup.Flavour.values()) {
            assertTrue(MariaDbBackup.tlsOptions(s, f).isEmpty());
        }
        assertFalse(MariaDbBackup.command(s, Path.of("b.sql")).stream().anyMatch(a -> a.contains("ssl")));
    }

    @Test
    public void mysqlGetsSslModeAndTheCaFile() {
        assertEquals(List.of("--ssl-mode=REQUIRED"),
                MariaDbBackup.tlsOptions(settings("db.type", "mariadb", "db.sslMode", "trust"), MariaDbBackup.Flavour.MYSQL));
        assertEquals(List.of("--ssl-mode=VERIFY_IDENTITY"),
                MariaDbBackup.tlsOptions(settings("db.type", "mariadb", "db.ssl", "true"), MariaDbBackup.Flavour.MYSQL));
        assertEquals(List.of("--ssl-mode=VERIFY_CA"),
                MariaDbBackup.tlsOptions(settings("db.type", "mariadb", "db.sslMode", "verify-ca"), MariaDbBackup.Flavour.MYSQL));
    }

    @Test
    public void mariadbGetsSslAndVerifyServerCert() {
        assertEquals(List.of("--ssl"),
                MariaDbBackup.tlsOptions(settings("db.type", "mariadb", "db.sslMode", "trust"), MariaDbBackup.Flavour.MARIADB));
        assertEquals(List.of("--ssl", "--ssl-verify-server-cert"),
                MariaDbBackup.tlsOptions(settings("db.type", "mariadb", "db.ssl", "true"), MariaDbBackup.Flavour.MARIADB));
        assertEquals("checking the CA also checks the host name there: stricter, never weaker",
                List.of("--ssl", "--ssl-verify-server-cert"),
                MariaDbBackup.tlsOptions(settings("db.type", "mariadb", "db.sslMode", "verify-ca"), MariaDbBackup.Flavour.MARIADB));
    }

    @Test
    public void theServerCertificateFileIsPassedToBothFamilies() throws Exception {
        Path cert = java.nio.file.Files.createTempFile("ca", ".pem");
        try {
            DatabaseSettings s = settings("db.type", "mariadb", "db.sslMode", "verify-full", "db.sslServerCert", cert.toString());
            assertTrue(MariaDbBackup.tlsOptions(s, MariaDbBackup.Flavour.MYSQL).contains("--ssl-ca=" + s.sslServerCert()));
            assertTrue(MariaDbBackup.tlsOptions(s, MariaDbBackup.Flavour.MARIADB).contains("--ssl-ca=" + s.sslServerCert()));
            assertTrue(MariaDbBackup.command(s, Path.of("b.sql"), MariaDbBackup.Flavour.MYSQL)
                    .contains("--ssl-mode=VERIFY_IDENTITY"));
            List<String> all = MariaDbBackup.command(s, Path.of("b.sql"), MariaDbBackup.Flavour.MARIADB);
            assertEquals("the database name still comes last", s.database(), all.get(all.size() - 1));
        } finally {
            java.nio.file.Files.deleteIfExists(cert);
        }
    }

    @Test
    public void anEncryptedConnectionWithAnUnknownProgramIsRefusedNotSentInTheClear() {
        DatabaseSettings s = settings("db.type", "mariadb", "db.ssl", "true");
        try {
            MariaDbBackup.command(s, Path.of("b.sql"), MariaDbBackup.Flavour.UNKNOWN);
            org.junit.Assert.fail("expected a refusal");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("mysqldump"));
        }
    }

    @Test
    public void thePasswordStillNeverGoesOnTheCommandLineWithTls() {
        DatabaseSettings s = settings("db.type", "mariadb", "db.ssl", "true", "db.password", "topsecret");
        for (String arg : MariaDbBackup.command(s, Path.of("b.sql"), MariaDbBackup.Flavour.MYSQL)) {
            assertFalse(arg.contains("topsecret"));
        }
    }

    /** A stand-in that answers --version like the given client and otherwise writes its own arguments to the result file. */
    private static java.nio.file.Path fakeClient(String versionLine) throws java.io.IOException {
        return script("out=\"\"\nfor a in \"$@\"; do\n  case \"$a\" in\n    --version) echo '" + versionLine + "'; exit 0 ;;\n"
                + "    --result-file=*) out=\"${a#--result-file=}\" ;;\n  esac\ndone\necho \"-- dump of $*\" > \"$out\"\nexit 0");
    }

    @Test(timeout = 20_000)
    public void anEncryptedConnectionAsksTheProgramWhichKindItIsAndPassesItsOwnOptions() throws Exception {
        org.junit.Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
        java.nio.file.Path target = java.nio.file.Files.createTempDirectory("dump").resolve("b.sql");
        MariaDbBackup.dumpTo(settings("db.type", "mariadb", "db.ssl", "true",
                "db.mysqldump", fakeClient("mysqldump  Ver 10.19 Distrib 10.11.6-MariaDB, for Linux").toString()), target);
        String written = java.nio.file.Files.readString(target);
        assertTrue(written, written.contains("--ssl --ssl-verify-server-cert"));
        assertFalse(written, written.contains("--ssl-mode"));

        java.nio.file.Path target2 = java.nio.file.Files.createTempDirectory("dump").resolve("b.sql");
        MariaDbBackup.dumpTo(settings("db.type", "mariadb", "db.sslMode", "verify-ca",
                "db.mysqldump", fakeClient("mysqldump  Ver 8.0.36 for Linux on x86_64 (MySQL Community Server - GPL)").toString()), target2);
        String written2 = java.nio.file.Files.readString(target2);
        assertTrue(written2, written2.contains("--ssl-mode=VERIFY_CA"));
    }

    @Test(timeout = 20_000)
    public void anEncryptedConnectionWithAProgramThatDoesNotSayWhatItIsMakesNoDumpAtAll() throws Exception {
        org.junit.Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
        java.nio.file.Path target = java.nio.file.Files.createTempDirectory("dump").resolve("b.sql");
        try {
            MariaDbBackup.dumpTo(settings("db.type", "mariadb", "db.ssl", "true",
                    "db.mysqldump", fakeClient("some tool 1.0").toString()), target);
            org.junit.Assert.fail("must refuse");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("db.mysqldump"));
        }
        assertFalse("nothing was dumped, in the clear or otherwise", java.nio.file.Files.exists(target));
    }

    @Test(timeout = 20_000)
    public void anUnencryptedConnectionDoesNotRunTheVersionProbe() throws Exception {
        org.junit.Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
        java.nio.file.Path target = java.nio.file.Files.createTempDirectory("dump").resolve("b.sql");
        MariaDbBackup.dumpTo(settings("db.type", "mariadb", "db.mysqldump", fakeClient("some tool 1.0").toString()), target);
        assertTrue(java.nio.file.Files.readString(target).startsWith("-- dump of"));
    }
}
