package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** TODO 6.68: the standard repository files exist and say what the project relies on (read from the project folder, as {@code mvn test} runs there). */
public class RepositoryFilesTest {

    private static String read(String path) throws IOException {
        assertTrue(path + " is missing", Files.isRegularFile(Paths.get(path)));
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    @Test
    public void editorconfigFixesLineEndingsAndIndentation() throws IOException {
        String text = read(".editorconfig");
        assertTrue(text.contains("root = true"));
        assertTrue(text.contains("end_of_line = lf"));
        assertTrue(text.contains("indent_style = space"));
        assertTrue(text.contains("indent_size = 4"));
    }

    @Test
    public void securityPolicySaysWhereToReport() throws IOException {
        String text = read("SECURITY.md");
        assertTrue(text.contains("Report a vulnerability"));
        assertTrue(text.contains("do not open a public issue"));
    }

    @Test
    public void theTemplatesAskForTheThingsThatMatter() throws IOException {
        String pr = read(".github/pull_request_template.md");
        assertTrue(pr.contains("TODO"));
        assertTrue(pr.contains("./mvnw -B clean test"));
        assertTrue(read(".github/ISSUE_TEMPLATE/bug_report.md").contains("SECURITY.md"));
        read(".github/ISSUE_TEMPLATE/feature_request.md");
    }
}
