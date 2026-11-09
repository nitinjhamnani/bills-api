package com.b2bfintech.app.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillerListQueryTest {

    @Test
    void smallCategoryReturnsEveryOperator() {
        BillerListQuery.Page<String> page = BillerListQuery.page(names(96), null, name -> name);

        assertFalse(page.searchRequired());
        assertEquals(96, page.billers().size());
    }

    @Test
    void largeCategoryWaitsForASearch() {
        BillerListQuery.Page<String> page = BillerListQuery.page(names(201), "t", name -> name);

        assertTrue(page.searchRequired());
        assertTrue(page.billers().isEmpty());
    }

    @Test
    void largeCategoryReturnsAtMostFiftyMatches() {
        List<String> names = names(250);
        names.set(0, "Tata Power - Delhi");
        names.set(1, "Tata Power - Mumbai");
        for (int i = 2; i < names.size(); i++) {
            names.set(i, "Tata " + i);
        }

        BillerListQuery.Page<String> page = BillerListQuery.page(names, "  tata ", name -> name);

        assertTrue(page.searchRequired());
        assertEquals(BillerListQuery.SEARCH_RESULT_LIMIT, page.billers().size());
        assertEquals("Tata Power - Delhi", page.billers().get(0));
    }

    private static List<String> names(int count) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add("Operator " + i);
        }
        return names;
    }
}
