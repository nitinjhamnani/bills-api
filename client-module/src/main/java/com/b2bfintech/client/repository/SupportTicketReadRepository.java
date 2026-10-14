package com.b2bfintech.client.repository;

import com.b2bfintech.client.entity.SupportTicketRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportTicketReadRepository extends JpaRepository<SupportTicketRead, UUID> {
    Optional<SupportTicketRead> findByTicketIdAndUserId(UUID ticketId, UUID userId);

    List<SupportTicketRead> findByUserIdAndTicketIdIn(UUID userId, Collection<UUID> ticketIds);
}
