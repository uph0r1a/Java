package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.81: the coverage floors in {@code pom.xml} can only go up. Lowering one means editing the baseline file as well, which shows in
 * the same change, next to the line in TODO.md that must say why.
 */
public class CoverageFloorTest {

    private static final String[] PACKAGES = {"service", "dao", "util", "config", "model"};

    private static double pomFloor(String pom, String pkg) {
        Matcher m = Pattern.compile("<coverage\\.min\\." + pkg + ">([0-9.]+)</coverage\\.min\\." + pkg + ">").matcher(pom);
        assertTrue("pom.xml has no coverage.min." + pkg, m.find());
        return Double.parseDouble(m.group(1));
    }

    @Test
    public void everyFloorIsInThePomAndNotBelowTheBaseline() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        Properties baseline = new Properties();
        try (var in = Files.newBufferedReader(Path.of("src", "test", "resources", "coverage", "floors-baseline.properties"))) {
            baseline.load(in);
        }
        for (String pkg : PACKAGES) {
            double floor = pomFloor(pom, pkg), lowest = Double.parseDouble(baseline.getProperty(pkg));
            assertTrue(pkg + ": a floor is a fraction from 0 to 1", floor >= 0 && floor <= 1);
            assertTrue(pkg + ": the floor " + floor + " is lower than the baseline " + lowest
                    + "; lowering it needs the baseline edited AND a line in TODO.md saying why", floor >= lowest);
        }
        assertEquals("the baseline names exactly these packages", PACKAGES.length, baseline.size());
    }

    @Test
    public void everyFloorIsUsedByACheckRuleOnItsPackage() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        for (String pkg : PACKAGES) {
            assertTrue(pkg, pom.contains("<include>com.coffeeshop.coffeeshopmanagement." + pkg + "</include>"));
            assertTrue(pkg, pom.contains("<minimum>${coverage.min." + pkg + "}</minimum>"));
            assertTrue("the package exists", Files.isDirectory(Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", pkg)));
        }
    }

    @Test
    public void theCiShowsTheMeasuredNumbersSoTheFloorsCanBeSetFromThem() throws IOException {
        assertTrue(Files.readString(Path.of(".github", "workflows", "ci.yml")).contains("Line coverage per package"));
    }
}
