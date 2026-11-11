package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** TODO 6.79: the bug finder, the bill of materials and the checksum are wired in, and none of them can slip into the normal build. */
public class BugFinderSetupTest {

    private static String read(String first, String... more) throws IOException {
        return Files.readString(Path.of(first, more));
    }

    @Test
    public void spotBugsAndFindSecBugsAreInAProfileWithPinnedVersions() throws IOException {
        String pom = read("pom.xml");
        int profile = pom.indexOf("<id>bugs</id>");
        assertTrue("the profile 'bugs'", profile > 0);
        String block = pom.substring(profile, pom.indexOf("</profile>", profile));
        assertTrue(Pattern.compile("spotbugs-maven-plugin</artifactId>\\s*<version>\\d+\\.\\d+\\.\\d+(\\.\\d+)?</version>").matcher(block).find());
        assertTrue(Pattern.compile("findsecbugs-plugin</artifactId>\\s*<version>\\d+\\.\\d+\\.\\d+</version>").matcher(block).find());
        assertTrue("report only at first", block.contains("<failOnError>false</failOnError>"));
        assertFalse("a profile that is not switched on by default", block.contains("<activeByDefault>"));
        assertFalse("not bound to a phase: the normal build must not run it", block.contains("<phase>"));
        assertTrue("the exclude file the pom names exists", Files.exists(Path.of("spotbugs-exclude.xml")));
    }

    @Test
    public void theBuildStampIsAFixedTimeSoTheJarIsTheSameEveryTime() throws IOException {
        assertTrue(Pattern.compile("<project.build.outputTimestamp>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z</project.build.outputTimestamp>")
                .matcher(read("pom.xml")).find());
    }

    @Test
    public void theCycloneDxPluginIsPinned() throws IOException {
        assertTrue(Pattern.compile("cyclonedx-maven-plugin</artifactId>\\s*<version>\\d+\\.\\d+\\.\\d+</version>").matcher(read("pom.xml")).find());
    }

    @Test
    public void theCiRunsTheBugFinderWithoutFailingTheBuild() throws IOException {
        String ci = read(".github", "workflows", "ci.yml");
        int job = ci.indexOf("\n  bugs:");
        assertTrue(job > 0);
        String block = ci.substring(job, ci.indexOf("\n  test-mariadb:", job) > 0 ? ci.indexOf("\n  test-mariadb:", job) : ci.length());
        assertTrue(block.contains("continue-on-error: true"));
        assertTrue(block.contains("-Pbugs"));
        assertTrue(block.contains("spotbugs:spotbugs"));
    }

    @Test
    public void theReleaseFingerprintsTheZipAndTheBillOfMaterialsAndStampsTheJar() throws IOException {
        String release = read(".github", "workflows", "release.yml");
        assertTrue(release.contains("cyclonedx-maven-plugin:makeBom"));
        assertTrue(release.contains("-Dproject.build.outputTimestamp="));
        assertTrue("the zip is copied before the fingerprints are written",
                release.indexOf("Copy-Item target/lunavera.zip") > 0
                        && release.indexOf("Copy-Item target/lunavera.zip") < release.indexOf("Get-FileHash $_.FullName"));
        assertTrue("the bill of materials is copied before the fingerprints are written",
                release.indexOf("lunavera-bom.json") > 0 && release.indexOf("lunavera-bom.json") < release.indexOf("Get-FileHash $_.FullName"));
    }

    @Test
    public void theOwnerIsToldHowToCheckTheZip() throws IOException {
        String docs = read("docs", "STATIC-ANALYSIS.md") + read("docs", "SIGNING.md");
        assertTrue(docs.contains("sha256sum lunavera.zip"));
        assertTrue(docs.contains("Get-FileHash .\\lunavera.zip"));
    }
}
