package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Which version of the program this is (TODO 5.41). It comes from {@code app-version.properties}, which Maven fills in from the pom
 * (the version and the build time) when it builds; in a build where that did not happen the answer is {@value #DEV}. It is shown on
 * the login screen and in Settings, and written at the start of every log and into every crash report, so "which version do you
 * have?" can be answered from a screenshot or the support file.
 */
public final class AppVersion {

    /** What the version is called when the program was not built by Maven (an IDE, a class folder). */
    public static final String DEV = "dev";
    static final String RESOURCE = "/app-version.properties";

    private static final String VERSION;
    private static final String BUILT;

    static {
        Properties p = new Properties();
        try (InputStream in = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (in != null) p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            // the version is a convenience: a missing file means "dev", never a program that does not start
        }
        VERSION = versionOf(p);
        BUILT = builtOf(p);
    }

    private AppVersion() {
    }

    /** The version text, or "dev": a value Maven did not fill in (it still holds a ${...}) or that is empty counts as not there. */
    static String versionOf(Properties p) {
        return clean(p.getProperty("version"), DEV);
    }

    /** The date the program was built (YYYY-MM-DD), or "" when unknown. */
    static String builtOf(Properties p) {
        String stamp = clean(p.getProperty("built"), "");
        return stamp.length() >= 10 && stamp.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}") ? stamp.substring(0, 10) : "";
    }

    private static String clean(String value, String fallback) {
        if (value == null) return fallback;
        String v = value.trim();
        return v.isEmpty() || v.contains("${") ? fallback : v;
    }

    /** For example {@code 1.0-SNAPSHOT}, or {@code dev}. */
    public static String version() {
        return VERSION;
    }

    /** The build date as {@code 2026-11-06}, or empty. */
    public static String built() {
        return BUILT;
    }

    /** The short line for a screen, in the current language: "Version 1.0-SNAPSHOT (built 2026-11-06)". */
    public static String label() {
        return BUILT.isEmpty() ? I18n.t("app.version", VERSION) : I18n.t("app.version.built", VERSION, BUILT);
    }

    /** The first line of a log and the header of a crash report: program, version, Java and system. Never in a language file. */
    public static String logLine() {
        return "Lunavera Coffee " + VERSION + (BUILT.isEmpty() ? "" : " (built " + BUILT + ")") + " | Java " + System.getProperty("java.version")
                + " | " + System.getProperty("os.name") + " " + System.getProperty("os.version");
    }
}
