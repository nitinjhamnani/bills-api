package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.SupportTicketService;
import com.b2bfintech.client.entity.SupportTicket;
import com.b2bfintech.client.entity.SupportTicketMessage;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A client raising and viewing its own support tickets. */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/support-tickets")
@PreAuthorize("hasAnyRole('CLIENT_ADMIN','CLIENT_OPERATOR','BRANCH_USER')")
public class ClientSupportTicketController {

    private final SupportTicketService service;

    public ClientSupportTicketController(SupportTicketService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDto raise(@PathVariable String clientId, @Valid @RequestBody RaiseRequest request,
                            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        SupportTicket ticket = service.raise(
                clientId, request.subject(), request.description(), principal.phoneNumber(),
                principal.userId(), actorName(principal));
        return toDto(ticket, principal, false);
    }

    @GetMapping
    public List<TicketDto> list(@PathVariable String clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        List<SupportTicket> tickets = service.listForClient(clientId);
        List<UUID> ids = tickets.stream().map(SupportTicket::getId).toList();
        Map<UUID, SupportTicketMessage> latest = service.latestMessages(ids);
        Map<UUID, Boolean> unread = service.unreadByTicket(
                principal.userId(), SupportTicketMessage.AuthorSide.ORGANISATION, ids);
        return tickets.stream()
                .map(t -> TicketDto.from(t, latest.get(t.getId()), unread.getOrDefault(t.getId(), false)))
                .toList();
    }

    @GetMapping("/unread-summary")
    public UnreadSummaryDto unreadSummary(@PathVariable String clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return new UnreadSummaryDto(service.unreadTicketCount(
                principal.userId(), SupportTicketMessage.AuthorSide.ORGANISATION, service.listForClient(clientId)));
    }

    @GetMapping("/{id}")
    public TicketDto get(@PathVariable String clientId, @PathVariable UUID id,
                          @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        SupportTicket ticket = service.getForClient(clientId, id);
        return toDto(ticket, principal, null);
    }

    @GetMapping("/{id}/messages")
    public List<MessageDto> listMessages(@PathVariable String clientId, @PathVariable UUID id,
                                          @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return service.listMessagesForClient(clientId, id, principal.userId()).stream().map(MessageDto::from).toList();
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto postMessage(@PathVariable String clientId, @PathVariable UUID id,
                                   @Valid @RequestBody MessageRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return MessageDto.from(service.addClientMessage(
                clientId, id, principal.userId(), actorName(principal), request.body()));
    }

    private TicketDto toDto(SupportTicket ticket, AuthenticatedUser principal, Boolean unreadOverride) {
        boolean unread = unreadOverride != null
                ? unreadOverride
                : service.unreadByTicket(
                        principal.userId(),
                        SupportTicketMessage.AuthorSide.ORGANISATION,
                        List.of(ticket.getId())
                ).getOrDefault(ticket.getId(), false);
        return TicketDto.from(ticket, service.latestMessages(List.of(ticket.getId())).get(ticket.getId()), unread);
    }

    public record UnreadSummaryDto(int unreadTicketCount) {
    }

    public record RaiseRequest(@NotBlank String subject, @NotBlank String description) {
    }

    public record MessageRequest(@NotBlank String body) {
    }

    public record TicketDto(String id, String subject, String description, String status,
                             String raisedBy, String resolvedBy, String resolutionNotes,
                             String lastMessagePreview, Instant lastMessageAt, boolean unread,
                             Instant createdAt, Instant updatedAt) {
        static TicketDto from(SupportTicket t, SupportTicketMessage last, boolean unread) {
            return new TicketDto(
                    t.getId().toString(), t.getSubject(), t.getDescription(), t.getStatus().name(),
                    t.getRaisedBy(), t.getResolvedBy(), t.getResolutionNotes(),
                    preview(last), last == null ? null : last.getCreatedAt(), unread,
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    public record MessageDto(String id, String ticketId, String authorSide, String authorUserId,
                              String authorName, String body, Instant createdAt) {
        static MessageDto from(SupportTicketMessage m) {
            return new MessageDto(
                    m.getId().toString(), m.getTicketId().toString(), m.getAuthorSide().name(),
                    m.getAuthorUserId(),
                    m.getAuthorName(), m.getBody(), m.getCreatedAt());
        }
    }

    private static String actorName(AuthenticatedUser principal) {
        if (principal.displayName() != null && !principal.displayName().isBlank()) {
            return principal.displayName();
        }
        return principal.phoneNumber();
    }

    private static String preview(SupportTicketMessage last) {
        if (last == null || last.getBody() == null) return null;
        String body = last.getBody().strip();
        return body.length() <= 140 ? body : body.substring(0, 137) + "...";
    }
}
