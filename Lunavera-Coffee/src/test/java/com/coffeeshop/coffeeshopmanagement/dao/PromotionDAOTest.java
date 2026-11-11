package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Kind;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Promotion;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Target;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** TODO 4.55: the promotions the shop has set up are kept, read back the same, and only an administrator changes them. */
public class PromotionDAOTest {

    private final PromotionDAO dao = new PromotionDAO();
    private final List<Integer> made = new ArrayList<>();
    private Category category;
    private Product product;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void shop() {
        TestSession.admin();
        category = new Category();
        category.setName("test-promo-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        product = new Product();
        product.setName("test-promo-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("40000"));
        product.setStock(10);
        product.setActive(true);
        product = new ProductDAO().insert(product);
    }

    @After
    public void cleanUp() {
        TestSession.admin();
        for (int id : made) {
            dao.delete(id);
        }
        made.clear();
        TestSession.clear();
    }

    private Promotion happyHour(String name) {
        return new Promotion(0, name, Kind.PERCENT, true, 10, 0, 0, 0, Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), LocalTime.of(14, 0),
                LocalTime.of(16, 30), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31), List.of(Target.category(category.getId())));
    }

    private Promotion save(Promotion promotion) {
        Promotion saved = dao.save(promotion);
        made.add(saved.id());
        return saved;
    }

    private Promotion find(int id) {
        return dao.findAll().stream().filter(p -> p.id() == id).findFirst().orElseThrow();
    }

    @Test
    public void aPromotionIsReadBackWithEverythingItWasGiven() {
        Promotion saved = save(happyHour("test-giờ vàng"));
        assertTrue(saved.id() > 0);
        Promotion read = find(saved.id());
        assertEquals("test-giờ vàng", read.name());
        assertEquals(Kind.PERCENT, read.kind());
        assertEquals(10, read.percent());
        assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), read.days());
        assertEquals(LocalTime.of(14, 0), read.from());
        assertEquals(LocalTime.of(16, 30), read.to());
        assertEquals(LocalDate.of(2026, 11, 1), read.validFrom());
        assertEquals(LocalDate.of(2026, 12, 31), read.validTo());
        assertTrue(read.active());
        assertEquals(List.of(Target.category(category.getId())), read.targets());
    }

    @Test
    public void aComboKeepsItsProductsAndPieceCounts() {
        Product second = new Product();
        second.setName("test-promo-second-" + UUID.randomUUID());
        second.setCategoryId(category.getId());
        second.setPrice(new BigDecimal("30000"));
        second.setStock(10);
        second.setActive(true);
        second = new ProductDAO().insert(second);
        Promotion combo = new Promotion(0, "test-combo", Kind.COMBO, true, 0, 55_000, 0, 0, Set.of(), null, null, null, null,
                List.of(Target.product(product.getId(), 1), Target.product(second.getId(), 2)));
        Promotion read = find(save(combo).id());
        assertEquals(55_000, read.fixedPrice());
        assertEquals(List.of(Target.product(product.getId(), 1), Target.product(second.getId(), 2)), read.targets());
    }

    @Test
    public void aPromotionWithNoDaysHoursOrDatesIsOnAllTheTime() {
        Promotion plain = new Promotion(0, "test-plain", Kind.FIXED_PRICE, true, 0, 30_000, 0, 0, Set.of(), null, null, null, null,
                List.of(Target.product(product.getId())));
        Promotion read = find(save(plain).id());
        assertTrue(read.days().isEmpty());
        assertEquals(null, read.from());
        assertEquals(null, read.to());
        assertEquals(null, read.validFrom());
        assertEquals(null, read.validTo());
    }

    @Test
    public void savingAnExistingPromotionReplacesItAndItsTargets() {
        Promotion first = save(happyHour("test-first"));
        Promotion changed = new Promotion(first.id(), "test-second", Kind.BUY_GET, true, 0, 0, 2, 1, Set.of(), null, null, null, null,
                List.of(Target.product(product.getId())));
        dao.save(changed);
        Promotion read = find(first.id());
        assertEquals("test-second", read.name());
        assertEquals(Kind.BUY_GET, read.kind());
        assertEquals(2, read.buy());
        assertEquals(1, read.free());
        assertEquals(0, read.percent());
        assertEquals(List.of(Target.product(product.getId())), read.targets());
        assertEquals("one row, not two", 1, dao.findAll().stream().filter(p -> p.id() == first.id()).count());
    }

    @Test
    public void aSwitchedOffPromotionIsNotAmongTheActiveOnes() {
        Promotion saved = save(happyHour("test-switch"));
        assertTrue(dao.findActive().stream().anyMatch(p -> p.id() == saved.id()));
        dao.setActive(saved.id(), false);
        assertFalse(dao.findActive().stream().anyMatch(p -> p.id() == saved.id()));
        assertFalse(find(saved.id()).active());
        dao.setActive(saved.id(), true);
        assertTrue(dao.findActive().stream().anyMatch(p -> p.id() == saved.id()));
    }

    @Test
    public void aDeletedPromotionIsGoneWithItsTargets() {
        Promotion saved = dao.save(happyHour("test-delete"));
        dao.delete(saved.id());
        assertFalse(dao.findAll().stream().anyMatch(p -> p.id() == saved.id()));
    }

    @Test
    public void aPromotionThatDoesNotPassTheRulesIsRefusedWithAPlainMessageAndNothingIsSaved() {
        int before = dao.findAll().size();
        Promotion bad = new Promotion(0, "test-bad", Kind.PERCENT, true, 150, 0, 0, 0, Set.of(), null, null, null, null,
                List.of(Target.product(product.getId())));
        try {
            dao.save(bad);
            fail("150% must be refused");
        } catch (DataAccessException expected) {
            assertNotNull(expected.getMessage());
            assertFalse(expected.getMessage().startsWith("!"));
        }
        assertEquals(before, dao.findAll().size());
    }

    @Test
    public void onlyAnAdministratorMakesChangesOrRemovesPromotions() {
        Promotion saved = save(happyHour("test-admin-only"));
        TestSession.clear(); // nobody signed in: not an administrator
        try {
            dao.save(happyHour("test-by-employee"));
            fail("an employee may not save a promotion");
        } catch (RuntimeException expected) {
            // fine
        }
        try {
            dao.setActive(saved.id(), false);
            fail("an employee may not switch a promotion");
        } catch (RuntimeException expected) {
            // fine
        }
        try {
            dao.delete(saved.id());
            fail("an employee may not delete a promotion");
        } catch (RuntimeException expected) {
            // fine
        }
        assertTrue("but the till can read them", dao.findActive().stream().anyMatch(p -> p.id() == saved.id()));
    }

    @Test
    public void theCategoriesOfProductsAreFound() {
        Map<Integer, Integer> categories = dao.categoriesOf(List.of(product.getId()));
        assertEquals(Integer.valueOf(category.getId()), categories.get(product.getId()));
        assertTrue(dao.categoriesOf(List.of()).isEmpty());
    }

    @Test
    public void removingAProductTakesItsTargetAwayAndALonelyPromotionIsIgnoredByTheRules() {
        Promotion saved = save(new Promotion(0, "test-orphan", Kind.PERCENT, true, 10, 0, 0, 0, Set.of(), null, null, null, null,
                List.of(Target.product(product.getId()))));
        assertEquals(1, find(saved.id()).targets().size());
        // (a product that was ever sold cannot be deleted; this one was not)
        new ProductDAO().delete(product.getId());
        Promotion read = find(saved.id());
        assertTrue(read.targets().isEmpty());
        assertEquals("targets", AutoPromotionRules.problemWith(read));
    }
}
