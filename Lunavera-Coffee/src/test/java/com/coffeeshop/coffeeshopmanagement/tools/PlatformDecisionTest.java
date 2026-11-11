package com.coffeeshop.coffeeshopmanagement.tools;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * TODO 6.89: {@code docs/RELEASE.md} carries a dated decision about moving the platform. When the JDK level in {@code pom.xml} changes,
 * the block must be rewritten (a decision that says "staying on 21" is not true for a project built for 25).
 */
public class PlatformDecisionTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    public void theReleaseNotesHoldADatedDecisionWithANextReview() throws IOException {
        String release = read("docs/RELEASE.md");
        int at = release.indexOf("## Platform versions: when to move");
        assertTrue("the block is missing", at >= 0);
        String block = release.substring(at, release.indexOf("\n## ", at + 5));
        assertTrue("no dated decision", Pattern.compile("Decision of 20\\d\\d-\\d\\d-\\d\\d").matcher(block).find());
        assertTrue("no next review", block.contains("Next review:"));
    }

    @Test
    public void theDecisionNamesTheJdkLevelThePomIsBuiltFor() throws IOException {
        Matcher release = Pattern.compile("<release>(\\d+)</release>").matcher(read("pom.xml"));
        assertTrue("no <release> in pom.xml", release.find());
        String level = release.group(1);
        String block = read("docs/RELEASE.md");
        assertTrue("RELEASE.md does not say which JDK the decision is about (pom.xml is built for " + level + ")",
                block.contains("`<release>" + level + "</release>`"));
    }
}
