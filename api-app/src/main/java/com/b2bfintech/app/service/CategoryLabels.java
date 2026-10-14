package com.b2bfintech.app.service;

import com.b2bfintech.platform.repository.BbpsCategoryRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

/** Category id to display name, from the synced BBPS master rather than a per-tenant copy. */
@Component
public class CategoryLabels {

    private final BbpsCategoryRepository categoryRepository;

    public CategoryLabels(BbpsCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Map<String, String> names() {
        return categoryRepository.namesById();
    }

    public Function<String, String> lookup() {
        Map<String, String> names = names();
        return code -> names.getOrDefault(code, code);
    }
}
