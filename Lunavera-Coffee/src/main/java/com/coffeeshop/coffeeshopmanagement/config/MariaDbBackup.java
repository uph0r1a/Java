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
        DatabaseSettings.SslMode mode = settings.sslMode();
        List<String> options = new ArrayList<>();
        if (mode == DatabaseSettings.SslMode.DISABLE) {
            return options;
        }
        String cert = settings.sslServerCert();
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
        List<String> command = new ArrayList<>();
        command.add(settings.mysqldump());
        command.add("--protocol=TCP");
        command.add("--host=" + settings.host());
        command.add("--port=" + settings.port());
        command.add("--user=" + settings.user());
        command.addAll(tlsOptions(settings, flavour));
        command.add("--single-transaction"); // a consistent copy while the shop keeps selling
        command.add(NO_TABLESPACES); // MySQL 8.0.21+ needs the PROCESS privilege to dump tablespaces; the shop's user has only its database (TODO 2.74)
        command.add("--default-character-set=utf8mb4");
        command.add("--result-file=" + target.toAbsolutePath());
        command.add(settings.database());
        return command;
    }

    static void dumpTo(DatabaseSettings settings, Path target) throws IOException {
        dumpTo(settings, target, TIMEOUT_SECONDS);
    }

    /**
     * Runs {@code mysqldump} and gives up after {@code timeoutSeconds}. The program's output is read by a small helper
     * thread, so this thread can wait with a real time limit: reading it here would block until the program ends and the
     * limit would never apply (TODO 2.73). A program that is still running at the limit is killed and the partial file removed.
     */
    static void dumpTo(DatabaseSettings settings, Path target, long timeoutSeconds) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // Only a connection that is encrypted needs to know which kind of mysqldump this is; the others run as before.
        Flavour flavour = settings.sslMode() == DatabaseSettings.SslMode.DISABLE ? Flavour.UNKNOWN : probeFlavour(settings);
        List<String> command;
        try {
            command = command(settings, target, flavour);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        Outcome outcome = run(settings, command, target, timeoutSeconds);
        if (outcome.exitValue() != 0 && rejectedNoTablespaces(outcome.output())) {
            // An old client that does not know the option (TODO 2.74): the same dump without it, as before.
            command.remove(NO_TABLESPACES);
            outcome = run(settings, command, target, timeoutSeconds);
        }
        if (outcome.exitValue() != 0) {
            Files.deleteIfExists(target); // never leave half a backup that looks complete
            throw new IOException(I18n.t("db.dump.error", outcome.exitValue(),
                    outcome.output().isEmpty() ? I18n.t("db.dump.nomessage") : outcome.output()));
        }
    }

    /** Runs {@code mysqldump --version} (a few seconds at most) and says which family it is. */
    private static Flavour probeFlavour(DatabaseSettings settings) throws IOException {
        Outcome outcome = run(settings, List.of(settings.mysqldump(), "--version"), null, VERSION_TIMEOUT_SECONDS);
        return flavourOf(outcome.output());
    }

    private static Outcome run(DatabaseSettings settings, List<String> command, Path target, long timeoutSeconds)
            throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
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
