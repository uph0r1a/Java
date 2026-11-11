package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** TODO 6.85: the database job of the CI runs on more than one server, MySQL 8 among them, and nothing in it is tied to one image. */
public class CiDatabaseMatrixTest {

    private static String job() throws IOException {
        String ci = Files.readString(Path.of(".github", "workflows", "ci.yml"), StandardCharsets.UTF_8);
        return ci.substring(ci.indexOf("\n  test-mariadb:"));
    }

    @Test
    public void theJobIsAMatrixOfSeveralServersIncludingMySql8() throws IOException {
        String job = job();
        for (String image : new String[]{"image: mariadb:10.6", "image: mariadb:10.11", "image: mariadb:11.4", "image: mysql:8.0"}) {
            assertTrue("missing " + image, job.contains(image));
        }
    }

    @Test
    public void noStepStillNamesOneFixedImageOrClient() throws IOException {
        String job = job();
        assertFalse("the container must be found through the matrix image", job.contains("ancestor=mariadb:"));
        assertFalse("the client must come from the matrix (MariaDB 11 has no mysql, MySQL has no mariadb)",
                job.contains("exec \"$container\" mariadb "));
        assertTrue(job.contains("ancestor=${{ matrix.image }}"));
    }

    @Test
    public void everyEntryHasTheFieldsTheStepsUse() throws IOException {
        String job = job();
        int entries = job.split("- name: ", -1).length - 1;
        for (String key : new String[]{"image: ", "client: ", "native_password: ", "experimental: ", "health: "}) {
            int found = job.split(java.util.regex.Pattern.quote("            " + key), -1).length - 1;
            assertTrue(key + "is missing in an entry (" + found + " of " + 4 + ")", found == 4);
        }
        assertTrue(entries >= 4);
    }
}
