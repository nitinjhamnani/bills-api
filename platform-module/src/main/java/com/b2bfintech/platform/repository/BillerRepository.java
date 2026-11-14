package com.b2bfintech.platform.repository;

import com.b2bfintech.platform.entity.BillerEntity;
import com.b2bfintech.platform.model.Biller;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillerRepository extends JpaRepository<BillerEntity, UUID> {

    List<BillerEntity> findByActiveTrueOrderByCategoryAscBillerNameAsc();

    List<BillerEntity> findByActiveTrueAndCategoryOrderByBillerNameAsc(String category);

    Optional<BillerEntity> findByBillerId(String billerId);

    default List<Biller> findAllActive() {
        return findByActiveTrueOrderByCategoryAscBillerNameAsc().stream().map(BillerEntity::toModel).toList();
    }

    default List<Biller> findActiveByCategory(String category) {
        return findByActiveTrueAndCategoryOrderByBillerNameAsc(category).stream().map(BillerEntity::toModel).toList();
    }
}
