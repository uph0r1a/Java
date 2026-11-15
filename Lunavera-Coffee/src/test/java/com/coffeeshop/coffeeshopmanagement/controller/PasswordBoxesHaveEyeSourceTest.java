package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * TODO 6.35: the eye that shows a typed password was only on the login screen. Every password box a screen builds in code is now
 * handed to {@code PasswordReveal.boxed(...)}, so a new one cannot be added without it.
 */
public class PasswordBoxesHaveEyeSourceTest {

    private static final Pattern NEW_PASSWORD_BOX = Pattern.compile("new PasswordField\\(\\)");
    private static final Pattern WITH_EYE = Pattern.compile("PasswordReveal\\.boxed\\(");

    private static int count(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int n = 0;
        while (matcher.find()) n++;
        return n;
    }

    @Test
    public void everyPasswordBoxBuiltInCodeHasAnEye() throws IOException {
        Path dir = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");
        List<Path> files;
        try (Stream<Path> walk = Files.list(dir)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        int boxes = 0;
        for (Path file : files) {
            String source = Files.readString(file);
            int made = count(NEW_PASSWORD_BOX, source);
            boxes += made;
            assertTrue(file.getFileName() + " builds " + made + " password box(es) and gives the eye to only "
                    + count(WITH_EYE, source) + " (use PasswordReveal.boxed)", count(WITH_EYE, source) >= made);
        }
        assertTrue("the check found no password box at all, so it looks at the wrong folder", boxes >= 10);
    }
}
