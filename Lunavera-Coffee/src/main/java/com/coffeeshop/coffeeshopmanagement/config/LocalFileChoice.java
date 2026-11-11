package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.DataPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * First start with no database server (TODO 5.40). The default database is MariaDB, so on a computer that has no server and no
 * {@code data/database.properties} the program could only show an error and stop; a one-till shop without a server then had to find
 * the folder and write {@code db.type=sqlite} by hand. This class holds the two decisions behind the button the start-up box now
 * offers, "Use the local file on this computer": WHEN it is offered ({@link #applies}) and WHAT it does ({@link #write}: a settings
 * file with {@code db.type=sqlite}, never over a file that is already there). It has no screen and no database in it, so it is
 * tested on its own; {@code LunaveraApplication} shows the box and starts the program again.
 *
 * <p>It is offered only when the person has NOT said which database they mean: no settings file, and no {@code lunavera.db.type}
 * system property. A shop that has a settings file for its server (and whose server is down today) is never offered a new empty
 * local shop in place of its data; it keeps the old text and fixes the server.
 */
public final class LocalFileChoice {

    private LocalFileChoice() {
    }

    /** The system property that names the database type; when it is set the person chose, so nothing is offered. */
    static final String TYPE_PROPERTY = "lunavera.db.type";

    /**
     * Is the local file to be offered? Only when the settings resolve to a server ({@code settings}), because that is what a first
     * start with no file gets, and nobody named the database: {@code settingsFileExists} is false and {@code typeSetByProperty}
     * is false.
     */
    public static boolean applies(DatabaseSettings settings, boolean settingsFileExists, boolean typeSetByProperty) {
        return settings != null && settings.isMariaDb() && !settingsFileExists && !typeSetByProperty;
    }

    /**
     * {@link #applies(DatabaseSettings, boolean, boolean)} for this computer now: the settings as read from the data folder and the
     * system properties, whether {@code data/database.properties} exists, and whether {@code lunavera.db.type} is set. False when
     * the settings cannot even be read (a damaged file is a different problem, which the start-up box already explains).
     */
    public static boolean applies() {
        try {
            String type = System.getProperty(TYPE_PROPERTY);
            return applies(DatabaseSettings.load(), DatabaseSettings.fileExists(), type != null && !type.isBlank());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when the local data file of this computer (the SQLite file in the data folder) is already there, with its data. */
    public static boolean localFileExists() {
        try {
            return Files.isRegularFile(localFile());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Where the local data file is (or would be made): the SQLite file in the data folder. */
    public static Path localFile() {
        return DataPaths.dataDir().resolve(DataPaths.DATABASE_FILE_NAME);
    }

    /** The text of the settings file this writes: a note for whoever opens it, and the one setting. */
    static String fileText() {
        String eol = System.lineSeparator();
        return "# Written by Lunavera at the first start: no database server was found and the local file on this computer was chosen." + eol
                + "# To use a MariaDB/MySQL server instead, replace this file with a copy of database.properties.example" + eol
                + "# (the guide is docs/DATABASE.md) and start the program again." + eol
                + "db.type=sqlite" + eol;
    }

    /**
     * Writes {@code <dataDir>/database.properties} with {@code db.type=sqlite} and returns its path. The folder is made when it is
     * not there. An existing settings file is NEVER replaced (it may hold a server's password): that is an error, and the file is
     * left exactly as it was.
     *
     * @throws IOException when the folder or the file cannot be written, or the settings file already exists
     */
    public static Path write(Path dataDir) throws IOException {
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve(DatabaseSettings.FILE_NAME);
        try {
            Files.writeString(file, fileText(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException e) {
            throw new IOException(file + " already exists; it was not changed", e);
        }
        return file;
    }

    /** {@link #write(Path)} in the data folder this run uses. */
    public static Path write() throws IOException {
        return write(DataPaths.dataDir());
    }
}
