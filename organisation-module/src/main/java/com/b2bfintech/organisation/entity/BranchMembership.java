package com.b2bfintech.organisation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/** One person signed in to one branch. The same person can have several rows. */
@Entity
@Table(name = "branch_user_mapping")
@IdClass(BranchMembership.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class BranchMembership {

    @Id
    @Column(name = "branch_id", nullable = false, length = 8)
    private String branchId;

    @Id
    @Column(name = "user_id", nullable = false, length = 6)
    private String userId;

    public static class Key implements Serializable {

        private String branchId;
        private String userId;

        public Key() {
        }

        public Key(String branchId, String userId) {
            this.branchId = branchId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(branchId, key.branchId) && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(branchId, userId);
        }
    }
}
