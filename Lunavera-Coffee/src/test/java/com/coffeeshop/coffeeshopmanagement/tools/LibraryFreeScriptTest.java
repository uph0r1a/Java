package com.coffeeshop.coffeeshopmanagement.tools;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** TODO 6.73: the quick-check script, its runner and its stand-ins are in the repository, and the README and the release list name it. */
public class LibraryFreeScriptTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    public void theScriptTheRunnerAndTheStandInsExist() {
        for (String file : new String[]{"tools/run-library-free-tests.sh", "tools/LibraryFreeRunner.java",
                "tools/standins/org/sqlite/SQLiteException.java", "tools/standins/com/coffeeshop/coffeeshopmanagement/util/AppStyle.java"}) {
            assertTrue(file + " is missing", Files.isRegularFile(Path.of(file)));
        }
    }

    @Test
    public void theReadmeAndTheReleaseListNameTheScript() throws IOException {
        assertTrue(read("README.md").contains("tools/run-library-free-tests.sh"));
        assertTrue(read("docs/RELEASE.md").contains("tools/run-library-free-tests.sh"));
    }
}
