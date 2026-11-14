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

    List<CategoryOperatorEntity> findByCategoryIdAndActiveTrueOrderByOperatorNameAsc(UUID categoryId);

    Optional<CategoryOperatorEntity> findFirstByOperatorIdAndActiveTrueOrderByOperatorParamsAsc(String operatorId);

    List<CategoryOperatorEntity> findByOperatorIdAndActiveTrue(String operatorId);

    default List<CategoryOperator> findActiveByCategory(UUID categoryId) {
        return findByCategoryIdAndActiveTrueOrderByOperatorNameAsc(categoryId).stream()
                .map(CategoryOperatorEntity::toModel)
                .toList();
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
