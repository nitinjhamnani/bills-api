package com.b2bfintech.client.repository;

import com.b2bfintech.client.entity.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    List<SupportTicket> findByClientIdOrderByCreatedAtDesc(UUID clientId);
    List<SupportTicket> findAllByOrderByCreatedAtDesc();
}
