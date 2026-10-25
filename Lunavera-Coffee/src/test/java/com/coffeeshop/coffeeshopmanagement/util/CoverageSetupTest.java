package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * TODO 6.59: the coverage report fails silently in one common way: surefire's own {@code argLine} replaces the one JaCoCo sets, so
 * the agent never reaches the test JVM and the report is empty. These checks read {@code pom.xml} so that cannot happen unnoticed.
 */
public class CoverageSetupTest {

    private static String pom() throws IOException {
        return Files.readString(Path.of("pom.xml"));
    }

    @Test
    public void jacocoIsDeclaredWithAnExplicitVersionAndBothGoals() throws IOException {
        String pom = pom();
        Matcher plugin = Pattern.compile(
                "<artifactId>jacoco-maven-plugin</artifactId>\\s*<version>(\\d+\\.\\d+\\.\\d+)</version>").matcher(pom);
        assertTrue("jacoco-maven-plugin needs an explicit version (TODO 6.33)", plugin.find());
        assertTrue(pom.contains("<goal>prepare-agent</goal>"));
        assertTrue(pom.contains("<goal>report</goal>"));
    }

    @Test
    public void theTestJvmKeepsTheAgentThatJacocoSets() throws IOException {
        String pom = pom();
        assertTrue("surefire's argLine must start with @{argLine}, or the JaCoCo agent is dropped",
                pom.contains("<argLine>@{argLine} "));
        assertTrue("an empty default for the property, so a build without JaCoCo still starts",
                Pattern.compile("<argLine\\s*>\\s*</argLine>|<argLine\\s*/>").matcher(pom).find());
    }

    @Test
    public void noMinimumFailsTheBuildYet() throws IOException {
        assertFalse("decide on a minimum only after reading the first report (TODO 6.59)", pom().contains("<goal>check</goal>"));
    }
}
