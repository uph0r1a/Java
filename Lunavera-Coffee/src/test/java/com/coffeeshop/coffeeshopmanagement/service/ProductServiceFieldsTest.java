package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.ImageStorage;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 6.78 (mutation sweep, profile pit-db): what {@link ProductService} does on its own, which the tests of
 * {@link CategoryProductServicesTest} did not see: every field of a saved product reaches the database, the permission is
 * answered before the duplicate name, the picture file goes with the last product that uses it, and the plain lookups answer
 * from the database. Runs on the shared test database.
 */
public class ProductServiceFieldsTest {

    private final ProductService products = new ProductService();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void signInAsAdmin() {
        TestSession.admin();
    }

    @After
    public void signOut() {
        TestSession.clear();
    }

    private Category newCategory() {
        Category category = new Category();
        category.setName("svc-save-category-" + UUID.randomUUID());
        category.setActive(true);
        return categoryDAO.insert(category);
    }

    private Product newProduct(Category category, String imagePath) {
        Product product = new Product();
        product.setName("svc-save-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("15000"));
        product.setStock(5);
        product.setActive(true);
        product.setImagePath(imagePath);
        return productDAO.insert(product);
    }

    private static String newCode() {
        // the service saves a code in capitals, so the test makes one in capitals
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private static ProductFormRules.Values values(String name, Category category, String imagePath, boolean active) {
        return new ProductFormRules.Values(name, category, new BigDecimal("32000"), new BigDecimal("12000"), 7, 3, newCode(),
                "a typed description", imagePath, active);
    }

    private static void signInAsEmployee() {
        User user = new User();
        user.setId(2);
        user.setUsername("test-employee");
        user.setRole(Role.EMPLOYEE);
        Session.start(user, null);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    // ---- save ----

    @Test
    public void saveWritesEveryFieldOfANewProduct() {
        Category category = newCategory();
        String name = "svc-save-new-" + UUID.randomUUID();
        String picture = "svc-save-" + UUID.randomUUID() + ".png";
        ProductFormRules.Values typed = values(name, category, picture, false);

        Product saved = products.save(null, typed);

        assertTrue(saved.getId() > 0);
        Product read = productDAO.findById(saved.getId()).orElseThrow();
        assertEquals(name, read.getName());
        assertEquals(Integer.valueOf(category.getId()), read.getCategoryId());
        assertMoney("32000", read.getPrice());
        assertMoney("12000", read.getCost());
        assertEquals(7, read.getStock());
        assertEquals(Integer.valueOf(3), read.getReorderLevel());
        assertEquals(typed.code(), read.getCode());
        assertEquals("a typed description", read.getDescription());
        assertEquals(picture, read.getImagePath());
        assertFalse("the box said: not on sale", read.isActive());
    }

    @Test
    public void saveWritesEveryFieldOfAnExistingProduct() {
        Category category = newCategory();
        Product existing = newProduct(category, null);
        String name = "svc-save-changed-" + UUID.randomUUID();
        String picture = "svc-save-" + UUID.randomUUID() + ".png";
        ProductFormRules.Values typed = values(name, category, picture, false);

        Product saved = products.save(existing, typed);

        assertEquals("the same row is changed, none is added", existing.getId(), saved.getId());
        Product read = productDAO.findById(existing.getId()).orElseThrow();
        assertEquals(name, read.getName());
        assertMoney("32000", read.getPrice());
        assertMoney("12000", read.getCost());
        assertEquals(Integer.valueOf(3), read.getReorderLevel());
        assertEquals(typed.code(), read.getCode());
        assertEquals("a typed description", read.getDescription());
        assertEquals(picture, read.getImagePath());
        assertFalse(read.isActive());
    }

    @Test
    public void aTakenNameIsRefusedWithTheBoxOwnTitleAndNothingIsSaved() {
        Category category = newCategory();
        Product other = newProduct(category, null);
        try {
            products.save(null, values(other.getName(), category, null, true));
            fail("the same name in the same category must be refused");
        } catch (ProductService.Refused refused) {
            assertEquals(I18n.t("products.dup.title"), refused.title());
            assertFalse(refused.title().isBlank());
        }
        long withThatName = productDAO.findAll().stream().filter(p -> other.getName().equals(p.getName())).count();
        assertEquals(1, withThatName);
    }

    @Test
    public void aTakenCodeIsRefusedWithTheCodeTitle() {
        Category category = newCategory();
        Product coded = newProduct(category, null);
        products.save(coded, values(coded.getName(), category, null, true));
        String code = productDAO.findById(coded.getId()).orElseThrow().getCode();
        ProductFormRules.Values sameCode = new ProductFormRules.Values("svc-save-other-" + UUID.randomUUID(), category,
                new BigDecimal("1000"), new BigDecimal("500"), 1, null, code, null, null, true);
        try {
            products.save(null, sameCode);
            fail("a code another product has must be refused");
        } catch (ProductService.Refused refused) {
            assertEquals(I18n.t("products.val.code.title"), refused.title());
        }
    }

    @Test
    public void anEmployeeIsToldTheyMayNotSaveBeforeAnythingAboutADuplicateName() {
        Category category = newCategory();
        Product other = newProduct(category, null);
        ProductFormRules.Values duplicate = values(other.getName(), category, null, true);
        signInAsEmployee();
        assertThrows(Permissions.PermissionDeniedException.class, () -> products.save(null, duplicate));
    }

    // ---- delete ----

    @Test
    public void anEmployeeIsToldTheyMayNotDeleteEvenAProductAnOrderUses() {
        Category category = newCategory();
        Product product = newProduct(category, null);
        OrderItem line = new OrderItem();
        line.setProductId(product.getId());
        line.setProductName(product.getName());
        line.setQuantity(1);
        line.setUnitPrice(product.getPrice());
        line.setLineTotal(product.getPrice());
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(product.getPrice());
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(product.getPrice());
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAt(AppClock.now());
        new OrderDAO().insert(order, List.of(line));
        assertEquals(1, products.orderReferences(product.getId()));

        signInAsEmployee();
        assertThrows(Permissions.PermissionDeniedException.class, () -> products.delete(product));
    }

    @Test
    public void theFileOfADeletedProductGoesWhenNoOtherProductUsesIt() throws Exception {
        Path file = ImageStorage.directory().resolve("svc-save-" + UUID.randomUUID() + ".png");
        Files.writeString(file, "x");
        try {
            Product product = newProduct(newCategory(), file.getFileName().toString());
            assertTrue(products.delete(product));
            assertFalse("nobody else uses the picture, so the file goes", Files.exists(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void theFileOfADeletedProductStaysWhileAnotherProductUsesIt() throws Exception {
        Path file = ImageStorage.directory().resolve("svc-save-" + UUID.randomUUID() + ".png");
        Files.writeString(file, "x");
        try {
            Category category = newCategory();
            Product first = newProduct(category, file.getFileName().toString());
            Product second = newProduct(category, file.getFileName().toString());
            assertTrue(products.delete(first));
            assertTrue("the second product still shows it", Files.exists(file));
            assertTrue(products.delete(second));
            assertFalse("the last one took it with it", Files.exists(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    // ---- the plain lookups ----

    @Test
    public void theLookupsAnswerFromTheDatabase() {
        Category category = newCategory();
        Product product = newProduct(category, null);

        assertTrue(products.findAll().stream().anyMatch(p -> p.getId() == product.getId()));
        assertEquals(product.getName(), products.findById(product.getId()).orElseThrow().getName());
        assertTrue(products.findById(-5).isEmpty());

        assertTrue(products.existsByNameInCategory(product.getName(), category.getId(), 0));
        assertFalse("its own row does not count", products.existsByNameInCategory(product.getName(), category.getId(), product.getId()));
        assertFalse(products.existsByNameInCategory("svc-save-nobody-" + UUID.randomUUID(), category.getId(), 0));
    }

    @Test
    public void countUsingImageCountsTheOtherProductsOnly() {
        Category category = newCategory();
        String picture = "svc-save-" + UUID.randomUUID() + ".png";
        Product first = newProduct(category, picture);
        newProduct(category, picture);
        assertEquals(1, products.countUsingImage(picture, first.getId()));
        assertEquals(2, products.countUsingImage(picture, 0));
    }

    @Test
    public void insertAllAddsEveryProductOfTheList() {
        Category category = newCategory();
        String a = "svc-save-import-a-" + UUID.randomUUID();
        String b = "svc-save-import-b-" + UUID.randomUUID();
        Product first = new Product();
        first.setName(a);
        first.setCategoryId(category.getId());
        first.setPrice(new BigDecimal("10000"));
        first.setStock(1);
        first.setActive(true);
        Product second = new Product();
        second.setName(b);
        second.setCategoryId(category.getId());
        second.setPrice(new BigDecimal("20000"));
        second.setStock(2);
        second.setActive(true);

        products.insertAll(List.of(first, second));

        List<Product> all = productDAO.findAll();
        assertEquals(1, all.stream().filter(p -> a.equals(p.getName())).count());
        assertEquals(1, all.stream().filter(p -> b.equals(p.getName())).count());
    }

    @Test
    public void moveSaysWhetherTheProductChangedPlace() {
        Category category = newCategory();
        newProduct(category, null);
        Product last = newProduct(category, null);
        assertTrue("the last one can go up", products.move(last.getId(), -1));
        assertFalse("and is then first: nowhere further up", products.move(last.getId(), -1));
    }
}
