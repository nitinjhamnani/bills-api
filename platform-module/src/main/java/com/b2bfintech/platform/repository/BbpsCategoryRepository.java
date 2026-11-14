package com.b2bfintech.platform.repository;

import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.platform.entity.BbpsCategoryEntity;
import com.b2bfintech.platform.model.BbpsCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface BbpsCategoryRepository extends JpaRepository<BbpsCategoryEntity, UUID> {

    List<BbpsCategoryEntity> findByActiveTrueOrderByNameAsc();

    Optional<BbpsCategoryEntity> findFirstByCategoryIdAndActiveTrue(String categoryId);

    Optional<BbpsCategoryEntity> findByProviderCodeAndCategoryIdAndActiveTrue(String providerCode, String categoryId);

    Optional<BbpsCategoryEntity> findByProviderCodeAndCategoryId(String providerCode, String categoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BbpsCategoryEntity c set c.active = false where c.providerCode = :providerCode")
    void deactivateAll(@Param("providerCode") String providerCode);

    default List<BbpsCategory> findAllActive() {
        return findByActiveTrueOrderByNameAsc().stream().map(BbpsCategoryEntity::toModel).toList();
    }

    default Optional<BbpsCategory> findActiveByCategoryId(String categoryId) {
        return findFirstByCategoryIdAndActiveTrue(categoryId).map(BbpsCategoryEntity::toModel);
    }

    default Optional<BbpsCategory> findActive(String providerCode, String categoryId) {
        return findByProviderCodeAndCategoryIdAndActiveTrue(providerCode, categoryId).map(BbpsCategoryEntity::toModel);
    }

    default Map<String, String> namesById() {
        Map<String, String> names = new LinkedHashMap<>();
        findAll().forEach(row -> names.put(row.getCategoryId(), row.getName()));
        return names;
    }

    @Transactional
    default void upsert(String providerCode, BbpsProvider.ProviderCategory category) {
        BbpsCategoryEntity row = findByProviderCodeAndCategoryId(providerCode, category.categoryId())
                .orElseGet(() -> {
                    BbpsCategoryEntity created = new BbpsCategoryEntity();
                    created.setId(UUID.randomUUID());
                    created.setProviderCode(providerCode);
                    created.setCategoryId(category.categoryId());
                    return created;
                });
        row.setName(category.name());
        row.setCategoryGroup(category.group());
        row.setActive(category.active());
        row.setSyncedAt(Instant.now());
        save(row);
    }
}
