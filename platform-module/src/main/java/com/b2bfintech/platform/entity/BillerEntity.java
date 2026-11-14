package com.b2bfintech.platform.entity;

import com.b2bfintech.platform.model.Biller;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "biller")
@Getter
@Setter
@NoArgsConstructor
public class BillerEntity {

    @Id
    private UUID id;

    @Column(name = "biller_id", nullable = false, length = 50)
    private String billerId;

    @Column(name = "biller_name", nullable = false, length = 200)
    private String billerName;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(length = 50)
    private String state;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public Biller toModel() {
        return new Biller(id, billerId, billerName, category, state, active);
    }
}
