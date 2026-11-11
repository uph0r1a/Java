package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Backs up a MariaDB/MySQL database by running the server package's own {@code mysqldump} program, which writes a
 * plain {@code .sql} text file that phpMyAdmin ("Import") or the {@code mysql} command can read back.
 *
 * <p>The password is handed over in the {@code MYSQL_PWD} environment variable, not on the command line, so it does
 * not show up in the list of running programs. {@code mysqldump} must be installed on the computer running the app;
 * if it is not on the PATH, set {@code db.mysqldump} to its full path in {@code data/database.properties}.
 *
 * <p>The TLS settings of the connection ({@code db.ssl}, {@code db.sslMode}, {@code db.sslServerCert}) are passed to
 * {@code mysqldump} too (TODO 2A.42), so the backup is encrypted and checked like the till's own queries, and works against a
 * server that requires TLS. The two families of client spell this differently, so {@code mysqldump --version} is asked first
 * (only when TLS is wanted) and the options of that family are built; see {@link #tlsOptions}.
 *
 * <p><b>Letters outside the system's ANSI code page (TODO 3.70).</b> {@code mysqldump.exe} is a native Windows program that reads its
 * arguments in the ANSI code page, so a path such as {@code C:\Users\Nguyễn Văn An\Sao lưu\b.sql} given as an argument can fail or
 * be written under another name; a folder or a backup file with Vietnamese letters is normal here. So no argument of the program ever
 * holds such a path: it is started <b>inside</b> the folder of the backup (the working directory is handed over as Unicode, which
 * Windows has no trouble with) and told to write a file with a plain ASCII name there ({@code lunavera-dump-<random>.part}); once it
 * has finished successfully Java moves that file to the real name, whatever letters it has. A server certificate whose path has such
 * letters is copied for the run into the same folder under a plain name. Output is not taken from standard output instead: on
 * Windows that turns every {@code \n} into {@code \r\n}, which is why {@code --result-file} exists.
 */
final class MariaDbBackup {

    private static final int OUTPUT_LIMIT = 2000;
    private static final long TIMEOUT_SECONDS = 180;
    private static final long VERSION_TIMEOUT_SECONDS = 15;

    static final String NO_TABLESPACES = "--no-tablespaces";

    private MariaDbBackup() {
    }

    /** True when a {@code mysqldump} that is too old to know {@code --no-tablespaces} refused it, so the dump can be tried without it. */
    static boolean rejectedNoTablespaces(String output) {
        String text = output == null ? "" : output.toLowerCase(java.util.Locale.ROOT);
        return text.contains("no-tablespaces") && (text.contains("unknown option") || text.contains("unrecognized option")
                || text.contains("unknown variable"));
    }

    private record Outcome(int exitValue, String output) {
    }

    /** Which family of {@code mysqldump} this is: they do not spell the TLS options the same way. */
    enum Flavour { MARIADB, MYSQL, UNKNOWN }

    /** Reads the answer of {@code mysqldump --version}: "10.19 Distrib 10.11.6-MariaDB" or "mariadb-dump from 11.4" vs "Ver 8.0.36 ... (MySQL Community Server)". */
    static Flavour flavourOf(String versionOutput) {
        String text = versionOutput == null ? "" : versionOutput.toLowerCase(java.util.Locale.ROOT);
        if (text.contains("mariadb")) {
            return Flavour.MARIADB;
        }
        if (text.contains("mysql")) {
            return Flavour.MYSQL;
        }
        return Flavour.UNKNOWN;
    }

    /**
     * The TLS options for {@code mysqldump} (TODO 2A.42). Nothing when the connection is not encrypted. MySQL's client has
     * {@code --ssl-mode}; MariaDB's has {@code --ssl} and {@code --ssl-verify-server-cert}, and no way to check the CA without
     * also checking the host name, so both "verify" modes ask for both there: stricter than the connection, never weaker, and
     * a failure is an error message, never a dump in the clear.
     *
     * @throws IllegalArgumentException when TLS is wanted and the family of the program is not known
     */
    static List<String> tlsOptions(DatabaseSettings settings, Flavour flavour) {
        return tlsOptions(settings, flavour, settings.sslServerCert());
    }

    /** The same, naming the certificate file the way the program is to be told (see {@link #certArgument}). */
    static List<String> tlsOptions(DatabaseSettings settings, Flavour flavour, String cert) {
        DatabaseSettings.SslMode mode = settings.sslMode();
        List<String> options = new ArrayList<>();
        if (mode == DatabaseSettings.SslMode.DISABLE) {
            return options;
        }
        boolean verify = mode == DatabaseSettings.SslMode.VERIFY_CA || mode == DatabaseSettings.SslMode.VERIFY_FULL;
        switch (flavour) {
            case MYSQL -> {
                options.add("--ssl-mode=" + switch (mode) {
                    case TRUST -> "REQUIRED";
                    case VERIFY_CA -> "VERIFY_CA";
                    default -> "VERIFY_IDENTITY";
                });
                if (verify && cert != null) {
                    options.add("--ssl-ca=" + cert);
                }
            }
            case MARIADB -> {
                options.add("--ssl");
                if (verify) {
                    options.add("--ssl-verify-server-cert");
                    if (cert != null) {
                        options.add("--ssl-ca=" + cert);
                    }
                }
            }
            default -> throw new IllegalArgumentException(I18n.t("db.dump.flavour.unknown", settings.mysqldump()));
        }
        return options;
    }

    /** The command for a connection that is not encrypted (the TLS options need the program's family; see the other overload). */
    static List<String> command(DatabaseSettings settings, Path target) {
        return command(settings, target, Flavour.UNKNOWN);
    }

    static List<String> command(DatabaseSettings settings, Path target, Flavour flavour) {
        return command(settings, target, flavour, settings.sslServerCert());
    }

    /**
     * The command line. {@code resultFile} and {@code cert} are written into it exactly as given, so the caller gives plain ASCII
     * names (see the class comment); {@link #dumpTo} does.
     */
    static List<String> command(DatabaseSettings settings, Path resultFile, Flavour flavour, String cert) {
        List<String> command = new ArrayList<>();
        command.add(programFor(settings.mysqldump()));
        command.add("--protocol=TCP");
        command.add("--host=" + settings.host());
        command.add("--port=" + settings.port());
        command.add("--user=" + settings.user());
        command.addAll(tlsOptions(settings, flavour, cert));
        command.add("--single-transaction"); // a consistent copy while the shop keeps selling
        command.add(NO_TABLESPACES); // MySQL 8.0.21+ needs the PROCESS privilege to dump tablespaces; the shop's user has only its database (TODO 2.74)
        command.add("--default-character-set=utf8mb4");
        command.add("--result-file=" + resultFile);
        command.add(settings.database());
        return command;
    }

    static void dumpTo(DatabaseSettings settings, Path target) throws IOException {
        dumpTo(settings, target, TIMEOUT_SECONDS);
    }

    /** True when the text has only plain ASCII characters (letters, digits, space, punctuation): safe as a program argument on any Windows. */
    static boolean isPlainAscii(String text) {
        if (text == null) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    /**
     * The program to start. A bare name ({@code mysqldump}) is looked up on the PATH as always; a relative path with folders in it
     * ({@code tools\\mysqldump.exe}) is made absolute first, because the program now starts in the backup's folder and would
     * otherwise be looked for there.
     */
    static String programFor(String configured) {
        try {
            Path path = Path.of(configured);
            if (path.getNameCount() > 1 && !path.isAbsolute()) {
                return path.toAbsolutePath().toString();
            }
        } catch (java.nio.file.InvalidPathException ignored) {
            // not a path the system can read; the program is started as written and the error says so
        }
        return configured;
    }

    /** A plain ASCII name for a file the program writes or reads in the backup's folder, never the same twice. */
    private static String plainName(String prefix, String suffix) {
        return prefix + Long.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFFFFFL) + suffix;
    }

    /**
     * Runs {@code mysqldump} and gives up after {@code timeoutSeconds}. The program's output is read by a small helper
     * thread, so this thread can wait with a real time limit: reading it here would block until the program ends and the
     * limit would never apply (TODO 2.73). A program that is still running at the limit is killed and the partial file removed.
     *
     * <p>The program writes {@code lunavera-dump-<random>.part} in the folder of {@code target}, where it runs, and only a finished
     * dump is moved to {@code target} (TODO 3.70, see the class comment); a failed one leaves nothing behind, and a file that was
     * already at {@code target} is left as it was.
     */
    static void dumpTo(DatabaseSettings settings, Path target, long timeoutSeconds) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Path folder = absoluteTarget.getParent() != null ? absoluteTarget.getParent() : absoluteTarget.getRoot();
        Files.createDirectories(folder);
        Path partial = folder.resolve(plainName("lunavera-dump-", ".part"));
        Path certCopy = null;
        try {
            // Only a connection that is encrypted needs to know which kind of mysqldump this is; the others run as before.
            Flavour flavour = settings.sslMode() == DatabaseSettings.SslMode.DISABLE ? Flavour.UNKNOWN : probeFlavour(settings);
            String cert = settings.sslServerCert();
            if (cert != null && !isPlainAscii(cert) && settings.sslMode() != DatabaseSettings.SslMode.DISABLE) {
                certCopy = folder.resolve(plainName("lunavera-ca-", ".pem"));
                try {
                    Files.copy(Path.of(cert), certCopy);
                } catch (IOException | java.nio.file.InvalidPathException e) {
                    throw new IOException(I18n.t("db.dump.cert.copy"), e);
                }
                cert = certCopy.getFileName().toString();
            }
            List<String> command;
            try {
                command = command(settings, partial.getFileName(), flavour, cert);
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage(), e);
            }
            Outcome outcome = run(settings, command, folder, partial, timeoutSeconds);
            if (outcome.exitValue() != 0 && rejectedNoTablespaces(outcome.output())) {
                // An old client that does not know the option (TODO 2.74): the same dump without it, as before.
                command.remove(NO_TABLESPACES);
                outcome = run(settings, command, folder, partial, timeoutSeconds);
            }
            if (outcome.exitValue() != 0) {
                throw new IOException(I18n.t("db.dump.error", outcome.exitValue(),
                        outcome.output().isEmpty() ? I18n.t("db.dump.nomessage") : outcome.output()));
            }
            if (!Files.isRegularFile(partial)) {
                throw new IOException(I18n.t("db.dump.nofile"));
            }
            moveInto(partial, absoluteTarget);
        } finally {
            Files.deleteIfExists(partial); // never leave half a backup that looks complete (a finished one was moved away above)
            if (certCopy != null) {
                Files.deleteIfExists(certCopy);
            }
        }
    }

    /** Puts the finished dump under its real name in one step where the system allows it, replacing an older file of that name. */
    private static void moveInto(Path finished, Path target) throws IOException {
        try {
            Files.move(finished, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(finished, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Runs {@code mysqldump --version} (a few seconds at most) and says which family it is. */
    private static Flavour probeFlavour(DatabaseSettings settings) throws IOException {
        Outcome outcome = run(settings, List.of(programFor(settings.mysqldump()), "--version"), null, null, VERSION_TIMEOUT_SECONDS);
        return flavourOf(outcome.output());
    }

    private static Outcome run(DatabaseSettings settings, List<String> command, Path workingDirectory, Path target, long timeoutSeconds)
            throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile()); // the folder of the backup; handed over as Unicode (TODO 3.70)
        }
        if (!settings.password().isEmpty()) {
            builder.environment().put("MYSQL_PWD", settings.password());
        }
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IOException(I18n.t("db.dump.notfound", settings.mysqldump(), e.getMessage()), e);
        }
        StringBuilder collected = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] bytes = in.readNBytes(OUTPUT_LIMIT);
                synchronized (collected) {
                    collected.append(new String(bytes, StandardCharsets.UTF_8).trim());
                }
                in.transferTo(java.io.OutputStream.nullOutputStream()); // let the program finish writing
            } catch (IOException ignored) {
                // the process ended or was killed
            }
        }, "mysqldump-output");
        reader.setDaemon(true);
        reader.start();
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                if (target != null) {
                    Files.deleteIfExists(target);
                }
                throw new IOException(I18n.t("db.dump.timeout", timeoutSeconds));
            }
            reader.join(5000); // the output of a finished program is complete within a moment
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException(I18n.t("db.dump.interrupted"), e);
        }
        String output;
        synchronized (collected) {
            output = collected.toString();
        }
        return new Outcome(process.exitValue(), output);
    }
}
