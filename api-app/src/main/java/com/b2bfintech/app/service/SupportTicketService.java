package com.b2bfintech.app.service;

import com.b2bfintech.client.entity.SupportTicket;
import com.b2bfintech.client.entity.SupportTicketMessage;
import com.b2bfintech.client.entity.SupportTicketRead;
import com.b2bfintech.client.repository.SupportTicketMessageRepository;
import com.b2bfintech.client.repository.SupportTicketReadRepository;
import com.b2bfintech.client.repository.SupportTicketRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Support tickets a client raises for the organisation to see and resolve — OPEN to
 * IN_PROGRESS to RESOLVED, with a message thread between the two sides. */
@Service
public class SupportTicketService {

    private final SupportTicketRepository repository;
    private final SupportTicketMessageRepository messageRepository;
    private final SupportTicketReadRepository readRepository;

    public SupportTicketService(
            SupportTicketRepository repository,
            SupportTicketMessageRepository messageRepository,
            SupportTicketReadRepository readRepository) {
        this.repository = repository;
        this.messageRepository = messageRepository;
        this.readRepository = readRepository;
    }

    @Transactional
    public SupportTicket raise(
            UUID clientId, String subject, String description, String raisedBy,
            UUID authorUserId, String authorName) {
        if (subject == null || subject.isBlank()) {
            throw new BadRequestException("subject is required");
        }
        if (description == null || description.isBlank()) {
            throw new BadRequestException("description is required");
        }
        SupportTicket ticket = new SupportTicket();
        ticket.setClientId(clientId);
        ticket.setSubject(subject.trim());
        ticket.setDescription(description.trim());
        ticket.setRaisedBy(raisedBy);
        ticket = repository.save(ticket);
        writeMessage(
                ticket.getId(), SupportTicketMessage.AuthorSide.CLIENT, authorUserId, authorName,
                description.trim());
        markRead(ticket.getId(), authorUserId);
        return ticket;
    }

    @Transactional
    public SupportTicket pickUp(UUID id, String actor) {
        SupportTicket ticket = getOrThrow(id);
        if (ticket.getStatus() != SupportTicket.Status.OPEN) {
            throw new BadRequestException("Ticket is not open");
        }
        ticket.setStatus(SupportTicket.Status.IN_PROGRESS);
        ticket.setPickedUpBy(actor);
        ticket = repository.save(ticket);
        writeMessage(
                ticket.getId(), SupportTicketMessage.AuthorSide.SYSTEM, null, "System",
                "Ticket picked up");
        return ticket;
    }

    @Transactional
    public SupportTicket resolve(UUID id, String actor, String resolutionNotes, UUID authorUserId, String authorName) {
        SupportTicket ticket = getOrThrow(id);
        if (ticket.getStatus() == SupportTicket.Status.RESOLVED) {
            throw new BadRequestException("Ticket is already resolved");
        }
        ticket.setStatus(SupportTicket.Status.RESOLVED);
        ticket.setResolvedBy(actor);
        ticket.setResolutionNotes(resolutionNotes);
        ticket = repository.save(ticket);
        writeMessage(
                ticket.getId(), SupportTicketMessage.AuthorSide.SYSTEM, null, "System",
                "Ticket resolved");
        if (resolutionNotes != null && !resolutionNotes.isBlank()) {
            writeMessage(
                    ticket.getId(), SupportTicketMessage.AuthorSide.ORGANISATION, authorUserId,
                    authorName, resolutionNotes.trim());
        }
        return ticket;
    }

    @Transactional
    public SupportTicketMessage addClientMessage(
            UUID clientId, UUID ticketId, UUID authorUserId, String authorName, String body) {
        SupportTicket ticket = getForClient(clientId, ticketId);
        assertCanReply(ticket);
        SupportTicketMessage message = writeMessage(
                ticket.getId(), SupportTicketMessage.AuthorSide.CLIENT, authorUserId, authorName,
                requireBody(body));
        markRead(ticket.getId(), authorUserId);
        return message;
    }

    @Transactional
    public SupportTicketMessage addOrganisationMessage(
            UUID ticketId, UUID authorUserId, String authorName, String body) {
        SupportTicket ticket = getOrThrow(ticketId);
        if (ticket.getStatus() == SupportTicket.Status.OPEN) {
            throw new BadRequestException("Pick up the ticket before sending a message");
        }
        assertCanReply(ticket);
        SupportTicketMessage message = writeMessage(
                ticket.getId(), SupportTicketMessage.AuthorSide.ORGANISATION, authorUserId, authorName,
                requireBody(body));
        markRead(ticket.getId(), authorUserId);
        return message;
    }

    @Transactional
    public List<SupportTicketMessage> listMessages(UUID ticketId, UUID userId) {
        getOrThrow(ticketId);
        markRead(ticketId, userId);
        return messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional
    public List<SupportTicketMessage> listMessagesForClient(UUID clientId, UUID ticketId, UUID userId) {
        getForClient(clientId, ticketId);
        markRead(ticketId, userId);
        return messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    public List<SupportTicket> listForClient(UUID clientId) {
        return repository.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    public List<SupportTicket> listAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public SupportTicket getForClient(UUID clientId, UUID ticketId) {
        SupportTicket ticket = getOrThrow(ticketId);
        if (!ticket.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("Ticket not found");
        }
        return ticket;
    }

    @Transactional
    public void markRead(UUID ticketId, UUID userId) {
        if (userId == null) return;
        SupportTicketRead read = readRepository.findByTicketIdAndUserId(ticketId, userId)
                .orElseGet(() -> {
                    SupportTicketRead created = new SupportTicketRead();
                    created.setTicketId(ticketId);
                    created.setUserId(userId);
                    return created;
                });
        read.setLastReadAt(Instant.now());
        readRepository.save(read);
    }

    public Map<UUID, Boolean> unreadByTicket(
            UUID userId, SupportTicketMessage.AuthorSide incomingSide, Collection<UUID> ticketIds) {
        Map<UUID, Boolean> unread = new LinkedHashMap<>();
        if (ticketIds == null || ticketIds.isEmpty()) {
            return unread;
        }
        for (UUID ticketId : ticketIds) {
            unread.put(ticketId, false);
        }
        if (userId == null) {
            return unread;
        }
        Map<UUID, Instant> latestIncoming = new HashMap<>();
        for (SupportTicketMessage message : messageRepository.findByTicketIdInOrderByCreatedAtDesc(ticketIds)) {
            if (message.getAuthorSide() != incomingSide) continue;
            latestIncoming.putIfAbsent(message.getTicketId(), message.getCreatedAt());
        }
        Map<UUID, Instant> lastRead = new HashMap<>();
        for (SupportTicketRead read : readRepository.findByUserIdAndTicketIdIn(userId, ticketIds)) {
            lastRead.put(read.getTicketId(), read.getLastReadAt());
        }
        for (UUID ticketId : ticketIds) {
            Instant incomingAt = latestIncoming.get(ticketId);
            if (incomingAt == null) continue;
            Instant readAt = lastRead.get(ticketId);
            unread.put(ticketId, readAt == null || incomingAt.isAfter(readAt));
        }
        return unread;
    }

    public int unreadTicketCount(UUID userId, SupportTicketMessage.AuthorSide incomingSide, List<SupportTicket> tickets) {
        return (int) unreadByTicket(userId, incomingSide, tickets.stream().map(SupportTicket::getId).toList())
                .values().stream().filter(Boolean::booleanValue).count();
    }

    public Map<UUID, SupportTicketMessage> latestMessages(Collection<UUID> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SupportTicketMessage> latest = new LinkedHashMap<>();
        for (SupportTicketMessage message : messageRepository.findByTicketIdInOrderByCreatedAtDesc(ticketIds)) {
            latest.putIfAbsent(message.getTicketId(), message);
        }
        return latest;
    }

    private SupportTicket getOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    private void assertCanReply(SupportTicket ticket) {
        if (ticket.getStatus() == SupportTicket.Status.RESOLVED) {
            throw new BadRequestException("Ticket is resolved");
        }
    }

    private String requireBody(String body) {
        if (body == null || body.isBlank()) {
            throw new BadRequestException("body is required");
        }
        return body.trim();
    }

    private SupportTicketMessage writeMessage(
            UUID ticketId, SupportTicketMessage.AuthorSide side, UUID authorUserId, String authorName, String body) {
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicketId(ticketId);
        message.setAuthorSide(side);
        message.setAuthorUserId(authorUserId);
        message.setAuthorName(authorName == null || authorName.isBlank() ? side.name() : authorName);
        message.setBody(body);
        return messageRepository.save(message);
    }
}
