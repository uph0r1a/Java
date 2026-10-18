package com.coffeeshop.coffeeshopmanagement.config;

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
 */
final class MariaDbBackup {

    private static final int OUTPUT_LIMIT = 2000;
    private static final long TIMEOUT_SECONDS = 180;

    private MariaDbBackup() {
    }

    static List<String> command(DatabaseSettings settings, Path target) {
        List<String> command = new ArrayList<>();
        command.add(settings.mysqldump());
        command.add("--protocol=TCP");
        command.add("--host=" + settings.host());
        command.add("--port=" + settings.port());
        command.add("--user=" + settings.user());
        command.add("--single-transaction"); // a consistent copy while the shop keeps selling
        command.add("--default-character-set=utf8mb4");
        command.add("--result-file=" + target.toAbsolutePath());
        command.add(settings.database());
        return command;
    }

    static void dumpTo(DatabaseSettings settings, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ProcessBuilder builder = new ProcessBuilder(command(settings, target)).redirectErrorStream(true);
        if (!settings.password().isEmpty()) {
            builder.environment().put("MYSQL_PWD", settings.password());
        }
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IOException("Không chạy được chương trình sao lưu \"" + settings.mysqldump() + "\". "
                    + "Hãy cài MariaDB/MySQL client (có sẵn trong XAMPP) hoặc ghi đường dẫn đầy đủ của mysqldump "
                    + "vào db.mysqldump trong data/database.properties. Chi tiết: " + e.getMessage(), e);
        }
        String output;
        try (InputStream in = process.getInputStream()) {
            byte[] bytes = in.readNBytes(OUTPUT_LIMIT);
            output = new String(bytes, StandardCharsets.UTF_8).trim();
            in.transferTo(java.io.OutputStream.nullOutputStream()); // let the program finish writing
        }
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                Files.deleteIfExists(target);
                throw new IOException("mysqldump chạy quá " + TIMEOUT_SECONDS + " giây nên đã bị dừng.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Việc sao lưu bị gián đoạn.", e);
        }
        if (process.exitValue() != 0) {
            Files.deleteIfExists(target); // never leave half a backup that looks complete
            throw new IOException("mysqldump báo lỗi (mã " + process.exitValue() + "): "
                    + (output.isEmpty() ? "không có thông báo" : output));
        }
    }
}
