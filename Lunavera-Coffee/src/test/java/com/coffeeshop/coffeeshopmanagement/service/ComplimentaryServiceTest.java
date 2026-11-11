package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Employee;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** TODO 6.21: who the free-drink box offers is worked out in the service, not in the screen. No database. */
public class ComplimentaryServiceTest {

    private static Employee person(String name, boolean active, boolean anonymised) {
        Employee e = new Employee();
        e.setFullName(name);
        e.setActive(active);
        if (anonymised) e.setAnonymisedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return e;
    }

    @Test
    public void onlyPeopleStillWorkingAndNotAnonymisedAreOffered() {
        Employee mai = person("Mai", true, false);
        Employee left = person("Nam", false, false);
        Employee gone = person("(removed)", true, true);
        Employee an = person("An", true, false);
        List<Employee> offered = ComplimentaryService.choosable(List.of(mai, left, gone, an));
        assertEquals(2, offered.size());
        assertSame("the order of the staff list is kept", mai, offered.get(0));
        assertSame(an, offered.get(1));
    }

    @Test
    public void nobodyIsOfferedWhenThereIsNobody() {
        assertEquals(0, ComplimentaryService.choosable(List.of()).size());
    }
}
