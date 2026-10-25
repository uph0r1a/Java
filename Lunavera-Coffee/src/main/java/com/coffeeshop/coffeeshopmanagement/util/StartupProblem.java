package com.coffeeshop.coffeeshopmanagement.util;

import java.nio.file.Path;

/**
 * The plain-language text shown when the database cannot be opened at start-up (TODO 2.5). Before, the exception
 * left {@code Application.init()} and the window simply never appeared; the only trace was in the log.
 */
public final class StartupProblem {

    private StartupProblem() {
    }

    public static String title() {
        return I18n.t("startup.title");
    }

    /** What to show: what happened in one line, the likely causes, where the data and the log are, and what to do. */
    public static String describe(Throwable error) {
        return describe(error, safe(DataPaths::dataDir), safe(DataPaths::logsDir), safe(DataPaths::backupsDir))
                + serverHint();
    }

    /**
     * Extra lines when the shop uses a MariaDB/MySQL server: the most common reason it will not open is that the
     * server is not running or the settings in data/database.properties are wrong. Empty for SQLite (and if the
     * settings themselves cannot be read, the technical detail above already says why).
     */
    static String serverHint() {
        try {
            return serverHint(com.coffeeshop.coffeeshopmanagement.config.DatabaseSettings.load(),
                    com.coffeeshop.coffeeshopmanagement.config.DatabaseSettings.fileExists());
        } catch (RuntimeException e) {
            return "";
        }
    }

    static String serverHint(com.coffeeshop.coffeeshopmanagement.config.DatabaseSettings settings, boolean settingsFileExists) {
        if (!settings.isMariaDb()) {
            return "";
        }
        String hint = "\n\n" + I18n.t("startup.server", settings.describe()) + "\n";
        if (!settingsFileExists) {
            hint += I18n.t("startup.nosettings", DataPaths.dataDir().resolve("database.properties")) + "\n";
        }
        return I18n.t("startup.check", hint, DataPaths.dataDir().resolve("database.properties"));
    }

    static String describe(Throwable error, String dataDir, String logsDir, String backupsDir) {
        StringBuilder text = new StringBuilder();
        text.append(I18n.t("startup.p1") + " ")
                .append(I18n.t("startup.p2") + "\n\n");
        text.append(I18n.t("startup.p3") + " ")
                .append(I18n.t("startup.p4") + "\n\n");
        text.append(I18n.t("startup.detail") + " ").append(rootMessage(error)).append("\n\n");
        text.append(I18n.t("startup.datadir") + " ").append(dataDir).append('\n');
        text.append(I18n.t("startup.logs") + " ").append(logsDir).append('\n');
        text.append(I18n.t("startup.backups") + " ").append(backupsDir).append("\n\n");
        text.append(I18n.t("startup.fix1") + " ")
                .append(I18n.t("startup.fix2") + " ")
                .append(I18n.t("startup.fix3"));
        return text.toString();
    }

    /** The deepest cause's message, or its class name when it has none. */
    static String rootMessage(Throwable error) {
        if (error == null) return I18n.t("startup.unknown");
        Throwable root = error;
        int guard = 0;
        while (root.getCause() != null && root.getCause() != root && guard++ < 20) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return (message == null || message.isBlank()) ? root.getClass().getSimpleName() : message;
    }

    private static String safe(java.util.concurrent.Callable<Path> path) {
        try {
            return path.call().toAbsolutePath().toString();
        } catch (Exception e) {
            return I18n.t("startup.undetermined");
        }
    }
}
