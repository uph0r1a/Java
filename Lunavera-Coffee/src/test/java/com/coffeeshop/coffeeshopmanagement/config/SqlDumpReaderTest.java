package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** TODO 1.8: the reader of the {@code lunavera.sql} text that a MariaDB restore runs. It needs no database. */
public class SqlDumpReaderTest {

    private static List<SqlDumpReader.Statement> read(String text) throws Exception {
        List<SqlDumpReader.Statement> found = new ArrayList<>();
        SqlDumpReader.read(new StringReader(text), found::add);
        return found;
    }

    private static final String DUMP = """
            -- MariaDB dump 10.19  Distrib 10.11.6-MariaDB, for Win64 (AMD64)
            --
            -- Host: 127.0.0.1    Database: lunavera
            /*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
            /*!40101 SET NAMES utf8mb4 */;
            /*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;

            DROP TABLE IF EXISTS `categories`;
            /*!40101 SET @saved_cs_client     = @@character_set_client */;
            CREATE TABLE `categories` (
              `id` int(11) NOT NULL AUTO_INCREMENT,
              `name` varchar(100) NOT NULL,
              PRIMARY KEY (`id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            /*!40101 SET character_set_client = @saved_cs_client */;

            LOCK TABLES `categories` WRITE;
            /*!40000 ALTER TABLE `categories` DISABLE KEYS */;
            INSERT INTO `categories` VALUES (1,'Cà phê'),(2,'It''s; tea'),(3,'a -- not a comment'),(4,'back\\\\slash\\'quote');
            /*!40000 ALTER TABLE `categories` ENABLE KEYS */;
            UNLOCK TABLES;
            -- Dump completed on 2026-11-02 10:00:00
            """;

    @Test
    public void aRealDumpIsCutIntoTheStatementsThatMatter() throws Exception {
        List<SqlDumpReader.Statement> all = read(DUMP);
        List<SqlDumpReader.Kind> kinds = all.stream().map(SqlDumpReader.Statement::kind).toList();
        assertEquals(List.of(SqlDumpReader.Kind.DROP_TABLE, SqlDumpReader.Kind.CREATE_TABLE, SqlDumpReader.Kind.SESSION,
                SqlDumpReader.Kind.INSERT, SqlDumpReader.Kind.SESSION), kinds);
        assertEquals("categories", all.get(1).table());
        assertEquals("categories", all.get(3).table());
    }

    @Test
    public void aSemicolonOrQuoteInsideATextEndsNothing() throws Exception {
        String insert = read(DUMP).get(3).sql();
        assertTrue(insert, insert.contains("'It''s; tea'"));
        assertTrue(insert, insert.contains("'a -- not a comment'"));
        assertTrue(insert, insert.contains("'back\\\\slash\\'quote'"));
        assertTrue("the whole row list is one statement", insert.endsWith("'back\\\\slash\\'quote')"));
    }

    @Test
    public void commentsAreDroppedAndNeverRun() throws Exception {
        for (SqlDumpReader.Statement s : read(DUMP)) {
            assertTrue(s.sql(), !s.sql().contains("/*") && !s.sql().contains("Dump completed"));
        }
    }

    @Test
    public void twoDashesInsideAnExpressionAreNotAComment() throws Exception {
        List<SqlDumpReader.Statement> all = read("INSERT INTO `t` VALUES (5--3);");
        assertEquals("INSERT INTO `t` VALUES (5--3)", all.get(0).sql());
    }

    @Test
    public void aLineThatIsOnlyAHashCommentIsSkipped() throws Exception {
        assertEquals(1, read("# note\nINSERT INTO `t` VALUES (1);").size());
    }

    private static void refused(String text, String part) throws Exception {
        try {
            read(text);
            fail("should have been refused: " + text);
        } catch (SqlDumpReader.UnsupportedDumpException expected) {
            assertTrue(expected.getMessage(), part == null || expected.getMessage().toUpperCase().contains(part));
        }
    }

    @Test
    public void whatAnAppDumpNeverHoldsIsRefused() throws Exception {
        refused("DROP DATABASE `lunavera`;", "DROP DATABASE");
        refused("GRANT ALL ON *.* TO 'x'@'%';", "GRANT");
        refused("DELIMITER ;;\nCREATE TRIGGER t BEFORE INSERT ON a FOR EACH ROW SET NEW.x = 1;;", "DELIMITER");
        refused("INSERT INTO `a` SELECT * FROM `b`;", "INSERT");
        refused("DELETE FROM `a`;", "DELETE");
        refused("UPDATE `a` SET x = 1;", "UPDATE");
        refused("CREATE TABLE `lunavera`.`a` (id int);", "CREATE");
        refused("DROP TABLE `a`;", "DROP");
        refused("CREATE DATABASE x;", "CREATE");
    }

    @Test
    public void theMessageNamesOnlyTheFirstWordsNotTheRows() throws Exception {
        try {
            read("UPDATE `customers` SET phone = '0912345678';");
            fail();
        } catch (SqlDumpReader.UnsupportedDumpException e) {
            assertTrue(e.getMessage(), !e.getMessage().contains("0912345678"));
        }
    }

    @Test
    public void aTextCutOffInTheMiddleIsRefused() throws Exception {
        refused("INSERT INTO `a` VALUES (1,'ab", null);
        refused("INSERT INTO `a` VALUES (1,2)", null); // no closing semicolon: the file stopped early
        refused("/* never closed", null);
    }

    @Test
    public void nothingAtAllIsNoStatements() throws Exception {
        assertEquals(0, read("").size());
        assertEquals(0, read("-- only a comment\n\n").size());
    }

    @Test
    public void aBigDumpIsReadAsAStreamOfSmallStatements() throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            text.append("INSERT INTO `t` VALUES (").append(i).append(",'x');\n");
        }
        int[] count = {0};
        SqlDumpReader.read(new StringReader(text.toString()), s -> count[0]++);
        assertEquals(20000, count[0]);
    }

    @Test
    public void whatTheSinkThrowsReachesTheCaller() throws Exception {
        try {
            SqlDumpReader.read(new StringReader("INSERT INTO `t` VALUES (1);"), s -> {
                throw new IOException("sink says no");
            });
            fail();
        } catch (IOException e) {
            assertEquals("sink says no", e.getMessage());
        }
    }
}
