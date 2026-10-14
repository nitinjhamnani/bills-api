package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    boolean existsByClientCode(String clientCode);
}
