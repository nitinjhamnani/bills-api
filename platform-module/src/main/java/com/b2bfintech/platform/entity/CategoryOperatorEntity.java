package com.b2bfintech.platform.entity;

import com.b2bfintech.platform.model.CategoryOperator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.util.UUID;

@Entity
@Table(name = "category_operators")
@Getter
@Setter
@NoArgsConstructor
public class CategoryOperatorEntity {

    @Id
    private UUID id;

    @Column(name = "operator_name", nullable = false, length = 300)
    private String operatorName;

    @Column(name = "operator_id", nullable = false, length = 80)
    private String operatorId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(length = 100)
    private String location;

    @Column(name = "operator_params", columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)")
    private String operatorParams;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public CategoryOperator toModel() {
        return new CategoryOperator(id, operatorName, operatorId, provider, location, operatorParams, categoryId, active);
    }
}
