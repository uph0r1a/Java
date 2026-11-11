package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.OptionKind;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.ProductOption;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 4.47: the sizes and extras of a product are saved as a whole list, checked, owner only, and copied to a category. */
public class ProductOptionsDaoTest {

    private final ProductOptionDAO dao = new ProductOptionDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private Category category;
    private Product latte;
    private Product mocha;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        TestSession.clear();
    }

    @Before
    public void fresh() {
        TestSession.admin();
        category = new Category();
        category.setName("test-opt-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        latte = product("40000");
        mocha = product("30000");
    }

    private Product product(String price) {
        Product p = new Product();
        p.setName("test-opt-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal(price));
        p.setStock(100);
        p.setActive(true);
        return productDAO.insert(p);
    }

    private static ProductOption size(String name, long price, int percent, boolean isDefault) {
        return new ProductOption(0, 0, OptionKind.SIZE, name, BigDecimal.valueOf(price), BigDecimal.valueOf(Math.max(0, price / 4)), percent, isDefault, true); // a cost may not be negative (OptionRules), so a cheaper size costs 0 extra
    }

    private static ProductOption extra(String name, long price) {
        return new ProductOption(0, 0, OptionKind.EXTRA, name, BigDecimal.valueOf(price), BigDecimal.ZERO, 100, false, true);
    }

    @Test
    public void aSavedListIsReadBackWithSizesFirstAndOneDefault() {
        dao.save(latte.getId(), List.of(extra("Extra shot", 10000), size("S", -5000, 80, false), size("M", 0, 100, false), size("L", 10000, 130, false)));
        List<ProductOption> read = dao.findByProduct(latte.getId());
        assertEquals(4, read.size());
        assertEquals("S", read.get(0).name());
        assertEquals("L", read.get(2).name());
        assertEquals("sizes come before extras", OptionKind.EXTRA, read.get(3).kind());
        assertEquals(0, new BigDecimal("-5000").compareTo(read.get(0).priceDelta()));
        assertEquals(130, read.get(2).recipePercent());
        assertEquals("the first size became the default", 1, read.stream().filter(ProductOption::isDefault).count());
        assertTrue(read.get(0).isDefault());
    }

    @Test
    public void savingAgainReplacesTheWholeList() {
        dao.save(latte.getId(), List.of(size("S", 0, 100, false), size("M", 0, 100, false)));
        dao.save(latte.getId(), List.of(extra("Cream", 5000)));
        List<ProductOption> read = dao.findByProduct(latte.getId());
        assertEquals(1, read.size());
        assertEquals("Cream", read.get(0).name());
        dao.save(latte.getId(), List.of());
        assertTrue(dao.findByProduct(latte.getId()).isEmpty());
    }

    @Test
    public void theTillOnlyGetsTheSwitchedOnOptionsAndOnlyOfProductsThatHaveSome() {
        ProductOption off = new ProductOption(0, 0, OptionKind.EXTRA, "Gold leaf", BigDecimal.TEN, BigDecimal.ZERO, 100, false, false);
        dao.save(latte.getId(), List.of(size("M", 0, 100, true), off));
        Map<Integer, List<ProductOption>> active = dao.findAllActive();
        assertEquals(1, active.get(latte.getId()).size());
        assertEquals("M", active.get(latte.getId()).get(0).name());
        assertFalse(active.containsKey(mocha.getId()));
        assertEquals("the owner still sees the switched-off one", 2, dao.findByProduct(latte.getId()).size());
    }

    @Test
    public void aBadListIsRefusedAndTheOldOneStays() {
        dao.save(latte.getId(), List.of(size("M", 0, 100, true)));
        for (List<ProductOption> bad : List.of(
                List.of(extra("", 1)),
                List.of(extra("Cream", 1), extra("cream", 2)),
                List.of(size("XS", -40001, 100, true)),
                List.of(extra("Coupon", -1)),
                List.of(size("L", 0, 900, true)))) {
            try {
                dao.save(latte.getId(), bad);
                fail("a bad list was saved");
            } catch (DataAccessException expected) {
                // refused
            }
        }
        assertEquals("M", dao.findByProduct(latte.getId()).get(0).name());
    }

    @Test
    public void onlyTheOwnerMaySaveOrCopy() {
        TestSession.clear();
        assertThrows(Permissions.PermissionDeniedException.class, () -> dao.save(latte.getId(), List.of(extra("Cream", 1))));
        assertThrows(Permissions.PermissionDeniedException.class, () -> dao.copyToCategory(latte.getId()));
        TestSession.admin();
    }

    @Test
    public void copyingGivesTheSameOptionsToEveryOtherProductOfTheCategory() {
        dao.save(latte.getId(), List.of(size("S", -5000, 80, false), size("L", 10000, 130, true), extra("Extra shot", 10000)));
        dao.save(mocha.getId(), List.of(extra("Old topping", 1)));
        Product elsewhere = new Product();
        Category other = new Category();
        other.setName("test-opt-other-" + UUID.randomUUID());
        other.setActive(true);
        new CategoryDAO().insert(other);
        elsewhere.setName("test-opt-" + UUID.randomUUID());
        elsewhere.setCategoryId(other.getId());
        elsewhere.setPrice(new BigDecimal("20000"));
        elsewhere.setStock(10);
        elsewhere.setActive(true);
        productDAO.insert(elsewhere);

        assertEquals(1, dao.copyToCategory(latte.getId()));
        List<ProductOption> copied = dao.findByProduct(mocha.getId());
        assertEquals(3, copied.size());
        assertEquals("S", copied.get(0).name());
        assertTrue("the default size is copied too", copied.get(1).isDefault());
        assertTrue("another category is untouched", dao.findByProduct(elsewhere.getId()).isEmpty());
        assertEquals("the source keeps its own", 3, dao.findByProduct(latte.getId()).size());
    }

    @Test
    public void copyingIsAllOrNothingWhenASizeDoesNotFitACheaperProduct() {
        Product cheap = product("4000");
        dao.save(cheap.getId(), List.of(extra("Keep me", 1)));
        dao.save(latte.getId(), List.of(size("XS", -10000, 80, true)));
        try {
            dao.copyToCategory(latte.getId());
            fail("a size that makes a product negative was copied");
        } catch (DataAccessException expected) {
            // refused
        }
        assertEquals("Keep me", dao.findByProduct(cheap.getId()).get(0).name());
        assertTrue("nothing was copied to the others either", dao.findByProduct(mocha.getId()).isEmpty());
    }

    @Test
    public void deletingAProductTakesItsOptionsWithIt() {
        Product gone = product("10000");
        dao.save(gone.getId(), List.of(size("M", 0, 100, true)));
        productDAO.delete(gone.getId());
        assertTrue(dao.findByProduct(gone.getId()).isEmpty());
    }
}
