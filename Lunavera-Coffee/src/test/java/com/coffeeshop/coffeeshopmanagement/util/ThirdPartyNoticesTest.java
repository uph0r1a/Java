package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * TODO 6.80: {@code THIRD-PARTY-NOTICES.md} names every library the program is shipped with, at the version in the pom, so raising a
 * version (or adding a library) cannot leave the notice behind; and the release puts the notices inside the program folder.
 */
public class ThirdPartyNoticesTest {

    private static String read(String first, String... more) throws IOException {
        return Files.readString(Path.of(first, more));
    }

    @Test
    public void everyLibraryInThePomIsNamedWithItsVersion() throws IOException {
        String pom = read("pom.xml");
        String notices = read("THIRD-PARTY-NOTICES.md");
        String dependencies = pom.substring(pom.indexOf("<dependencies>"), pom.indexOf("</dependencies>"));
        Matcher m = Pattern.compile("<artifactId>([^<]+)</artifactId>\\s*<version>([^<]+)</version>(\\s*<scope>test</scope>)?").matcher(dependencies);
        int shipped = 0;
        while (m.find()) {
            if (m.group(3) != null) continue; // a test library is not handed out
            shipped++;
            String artifact = m.group(1), version = m.group(2);
            // javafx-controls, -fxml and -media are one row ("OpenJFX"), named by its first artifact and the shared version
            String name = artifact.startsWith("javafx-") ? "javafx-" : artifact;
            assertTrue(artifact + " is not named in THIRD-PARTY-NOTICES.md", notices.contains(name));
            assertTrue(artifact + " " + version + ": the notice must give this version", notices.contains(version));
        }
        assertTrue("the pom lists the libraries the program ships with", shipped >= 5);
    }

    @Test
    public void theLicencesNamedAreTheOnesTheTextPromises() throws IOException {
        String notices = read("THIRD-PARTY-NOTICES.md");
        for (String needle : new String[]{"Classpath Exception", "Lesser General Public License v2.1", "Apache License 2.0", "public domain",
                "MIT", "SIL Open Font License 1.1", "Not legal advice"}) {
            assertTrue(needle, notices.contains(needle));
        }
        assertTrue("the font's licence text stays with the fonts", Files.exists(Path.of("src", "main", "resources", "fonts", "OFL.txt")));
        assertTrue(Files.exists(Path.of("LICENSE")));
    }

    @Test
    public void theReleasePutsTheNoticesInsideTheProgramFolderBeforeTheInstallerAndTheChecksums() throws IOException {
        String release = read(".github", "workflows", "release.yml");
        int copy = release.indexOf("THIRD-PARTY-NOTICES.md target/lunavera/");
        assertTrue("copied into the folder", copy > 0);
        assertTrue("before jpackage reads the folder", copy < release.indexOf("jpackage --type msi"));
        assertTrue("before the zip is fingerprinted", copy < release.indexOf("Get-FileHash $_.FullName"));
        assertTrue("the zip is made again after the copy", release.indexOf("Compress-Archive", copy) > copy);
    }

    @Test
    public void theReadmeSendsThePersonToTheNotices() throws IOException {
        assertTrue(read("README.md").contains("THIRD-PARTY-NOTICES.md"));
    }
}
