package com.coffeeshop.coffeeshopmanagement.dao;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** TODO 3.75: the count button saves the counted number, never "counted minus what the window showed" (no database needed to check). */
public class IngredientCountSourceTest {

    private static final String ROOT = "src/main/java/com/coffeeshop/coffeeshopmanagement/";

    @Test
    public void theCountButtonSavesTheNumberItself() throws IOException {
        String dialog = Files.readString(Path.of(ROOT + "controller/IngredientDialog.java"));
        assertTrue(dialog.contains("dao.setCounted(ingredient.id(), counted"));
        assertFalse("a difference from the shown stock", dialog.contains("counted - ingredient.stock()"));
    }

    @Test
    public void theCountIsAWriteOfTheNumberGuardedByTheRowItRead() throws IOException {
        String dao = Files.readString(Path.of(ROOT + "dao/IngredientDAO.java"));
        assertTrue(dao.contains("UPDATE ingredients SET stock = ? WHERE id = ? AND stock = ?"));
    }
}
