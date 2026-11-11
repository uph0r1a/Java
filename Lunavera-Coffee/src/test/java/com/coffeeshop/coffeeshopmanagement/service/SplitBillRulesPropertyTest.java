package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.76: random open orders split at random. Whatever is taken, the two parts add up to the order, no piece is lost or made up,
 * and a split that takes nothing or everything is refused. See {@link RandomCases} to replay a failure.
 */
public class SplitBillRulesPropertyTest {

    private static List<OrderItem> randomLines(Random random) {
        List<OrderItem> lines = new ArrayList<>();
        int count = RandomCases.between(random, 1, 6);
        for (int i = 0; i < count; i++) {
            OrderItem line = new OrderItem();
            line.setProductId(i + 1);
            line.setProductName("Product " + i);
            line.setQuantity(RandomCases.between(random, 1, 8));
            line.setUnitPrice(BigDecimal.valueOf(RandomCases.between(random, 1, 200) * 500L + random.nextInt(3)));
            line.setLineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
            lines.add(line);
        }
        return lines;
    }

    private static int pieces(List<OrderItem> lines) {
        int all = 0;
        for (OrderItem line : lines) {
            all += line.getQuantity();
        }
        return all;
    }

    @Test
    public void theTwoPartsAlwaysAddUpToTheOrder() {
        RandomCases.run("SplitBillRules.split", 5000, random -> {
            List<OrderItem> lines = randomLines(random);
            int all = pieces(lines);
            if (all < 2) {
                return; // a single piece cannot be split (taking it is an ordinary payment)
            }
            Map<Integer, Integer> take = new HashMap<>();
            int taken = 0;
            for (int i = 0; i < lines.size(); i++) {
                int n = RandomCases.between(random, 0, lines.get(i).getQuantity());
                if (n > 0) {
                    take.put(i, n);
                    taken += n;
                }
            }
            String problem = SplitBillRules.problem(lines, take);
            if (taken == 0 || taken >= all) {
                assertNotNull("taking nothing or everything is not a split", problem);
                return;
            }
            assertNull("a split that takes some and leaves some is good", problem);

            SplitBillRules.Split split = SplitBillRules.split(lines, take);
            BigDecimal order = SplitBillRules.sum(lines);
            assertEquals("the parts add up to the order", 0, order.compareTo(SplitBillRules.sum(split.part()).add(SplitBillRules.sum(split.remainder()))));
            assertEquals("pieces taken", taken, pieces(split.part()));
            assertEquals("pieces left", all - taken, pieces(split.remainder()));
            assertTrue("each part is a real part of the bill", SplitBillRules.sum(split.part()).signum() > 0 && SplitBillRules.sum(split.remainder()).signum() > 0);
            for (OrderItem piece : split.part()) {
                assertEquals("a line total is its unit price times its pieces", 0,
                        piece.getUnitPrice().multiply(BigDecimal.valueOf(piece.getQuantity())).compareTo(piece.getLineTotal()));
            }
            for (OrderItem line : lines) { // the cart's own lines are not touched
                assertEquals(0, line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())).compareTo(line.getLineTotal()));
            }
        });
    }

    @Test
    public void takingMorePiecesThanALineHasIsRefused() {
        RandomCases.run("SplitBillRules.problem, too many", 2000, random -> {
            List<OrderItem> lines = randomLines(random);
            int index = random.nextInt(lines.size());
            Map<Integer, Integer> take = Map.of(index, lines.get(index).getQuantity() + RandomCases.between(random, 1, 5));
            assertNotNull(SplitBillRules.problem(lines, take));
        });
    }
}
