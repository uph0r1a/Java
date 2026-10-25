package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** TODO 5.31: one arrow press swaps two neighbours of the same group and leaves everything else where it was. */
public class DisplayOrderTest {

    private record Item(int id, String group) {
    }

    private static List<Integer> move(List<Item> items, int id, int delta) {
        return DisplayOrder.moved(items, Item::id, Item::group, id, delta);
    }

    private static final List<Item> ONE_GROUP = List.of(new Item(1, "a"), new Item(2, "a"), new Item(3, "a"), new Item(4, "a"));

    @Test
    public void upSwapsWithThePreviousOne() {
        assertEquals(List.of(1, 3, 2, 4), move(ONE_GROUP, 3, -1));
    }

    @Test
    public void downSwapsWithTheNextOne() {
        assertEquals(List.of(2, 1, 3, 4), move(ONE_GROUP, 1, 1));
    }

    @Test
    public void theFirstCannotGoUpAndTheLastCannotGoDown() {
        assertEquals(List.of(1, 2, 3, 4), move(ONE_GROUP, 1, -1));
        assertEquals(List.of(1, 2, 3, 4), move(ONE_GROUP, 4, 1));
    }

    @Test
    public void anUnknownIdOrNoStepChangesNothing() {
        assertEquals(List.of(1, 2, 3, 4), move(ONE_GROUP, 99, 1));
        assertEquals(List.of(1, 2, 3, 4), move(ONE_GROUP, 2, 0));
    }

    @Test
    public void aStepOverOtherGroupsSwapsOnlyTheTwoOfTheSameGroup() {
        // a1 b1 a2 b2: moving a2 up trades places with a1; the b's stay exactly where they are
        List<Item> items = List.of(new Item(1, "a"), new Item(2, "b"), new Item(3, "a"), new Item(4, "b"));
        assertEquals(List.of(3, 2, 1, 4), move(items, 3, -1));
        assertEquals(List.of(3, 2, 1, 4), move(items, 1, 1)); // down from a1 reaches a2 over the b between them
    }

    @Test
    public void downOverOtherGroupsFindsTheNextOfTheSameGroup() {
        List<Item> items = List.of(new Item(1, "a"), new Item(2, "b"), new Item(3, "b"), new Item(4, "a"));
        assertEquals(List.of(4, 2, 3, 1), move(items, 1, 1));
    }

    @Test
    public void aLoneMemberOfItsGroupCannotMove() {
        List<Item> items = List.of(new Item(1, "a"), new Item(2, "b"), new Item(3, "a"));
        assertEquals(List.of(1, 2, 3), move(items, 2, -1));
        assertEquals(List.of(1, 2, 3), move(items, 2, 1));
    }

    @Test
    public void theGivenListIsNeverChanged() {
        List<Item> items = new java.util.ArrayList<>(ONE_GROUP);
        move(items, 2, 1);
        assertEquals(ONE_GROUP, items);
    }

    @Test
    public void aNullGroupIsAGroupLikeAnyOther() {
        List<Item> items = List.of(new Item(1, null), new Item(2, null));
        assertEquals(List.of(2, 1), move(items, 1, 1));
    }
}
