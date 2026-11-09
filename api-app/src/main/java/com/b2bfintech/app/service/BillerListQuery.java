package com.b2bfintech.app.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Small categories are returned whole. A category larger than {@link #FULL_LIST_LIMIT}
 * is searched, and only the first matches are returned.
 */
public final class BillerListQuery {

    public static final int FULL_LIST_LIMIT = 200;
    public static final int SEARCH_RESULT_LIMIT = 50;
    public static final int MIN_SEARCH_LENGTH = 2;

    private BillerListQuery() {
    }

    public static <T> Page<T> page(List<T> all, String query, Function<T, String> name) {
        List<T> source = all == null ? List.of() : all;
        boolean searchRequired = source.size() > FULL_LIST_LIMIT;
        if (!searchRequired) {
            return new Page<>(List.copyOf(source), false);
        }
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < MIN_SEARCH_LENGTH) {
            return new Page<>(List.of(), true);
        }
        String needle = trimmed.toLowerCase();
        List<T> matches = new ArrayList<>();
        for (T item : source) {
            String label = name.apply(item);
            if (label != null && label.toLowerCase().contains(needle)) {
                matches.add(item);
                if (matches.size() == SEARCH_RESULT_LIMIT) {
                    break;
                }
            }
        }
        return new Page<>(List.copyOf(matches), true);
    }

    public record Page<T>(List<T> billers, boolean searchRequired) {
    }
}
