package com.b2bfintech.platform.repository;

import com.b2bfintech.common.spi.bbps.BbpsProvider;
import com.b2bfintech.platform.entity.CategoryOperatorEntity;
import com.b2bfintech.platform.model.CategoryOperator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryOperatorRepository extends JpaRepository<CategoryOperatorEntity, UUID> {

    boolean existsByCategoryId(UUID categoryId);

    List<CategoryOperatorEntity> findByCategoryIdOrderByOperatorNameAsc(UUID categoryId);

    List<CategoryOperatorEntity> findByCategoryIdAndActiveTrueOrderByOperatorNameAsc(UUID categoryId);

    List<CategoryOperatorEntity> findByCategoryIdAndOperatorId(UUID categoryId, String operatorId);

    Optional<CategoryOperatorEntity> findFirstByOperatorIdAndActiveTrueOrderByOperatorParamsAsc(String operatorId);

    List<CategoryOperatorEntity> findByOperatorIdAndActiveTrue(String operatorId);

    default List<CategoryOperator> findAllByCategory(UUID categoryId) {
        return findByCategoryIdOrderByOperatorNameAsc(categoryId).stream()
                .map(CategoryOperatorEntity::toModel)
                .toList();
    }

    default List<CategoryOperator> findActiveByCategory(UUID categoryId) {
        return findByCategoryIdAndActiveTrueOrderByOperatorNameAsc(categoryId).stream()
                .map(CategoryOperatorEntity::toModel)
                .toList();
    }

    @Transactional
    default Optional<CategoryOperator> setOperatorActive(UUID categoryId, String operatorId, boolean active) {
        List<CategoryOperatorEntity> rows = findByCategoryIdAndOperatorId(categoryId, operatorId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        rows.forEach(row -> row.setActive(active));
        saveAll(rows);
        return Optional.of(rows.get(0).toModel());
    }

    default Optional<CategoryOperator> findActiveByOperatorId(String operatorId) {
        return findFirstByOperatorIdAndActiveTrueOrderByOperatorParamsAsc(operatorId).map(CategoryOperatorEntity::toModel);
    }

    @Transactional
    default void insertAll(UUID categoryId, String provider, List<BbpsProvider.ProviderBiller> operators) {
        saveAll(operators.stream().map(operator -> {
            CategoryOperatorEntity row = new CategoryOperatorEntity();
            row.setId(UUID.randomUUID());
            row.setOperatorName(operator.name());
            row.setOperatorId(operator.billerId());
            row.setProvider(provider);
            row.setCategoryId(categoryId);
            row.setActive(true);
            return row;
        }).toList());
    }

    @Transactional
    default void saveParams(String operatorId, String operatorParams) {
        List<CategoryOperatorEntity> rows = findByOperatorIdAndActiveTrue(operatorId);
        rows.forEach(row -> row.setOperatorParams(operatorParams));
        saveAll(rows);
    }
}
