package com.b2bfintech.client.repository;

import com.b2bfintech.client.entity.SupportTicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, UUID> {
    List<SupportTicketMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    List<SupportTicketMessage> findByTicketIdInOrderByCreatedAtDesc(Collection<UUID> ticketIds);
}
