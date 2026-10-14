package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.SupportTicketService;
import com.b2bfintech.client.entity.SupportTicket;
import com.b2bfintech.client.entity.SupportTicketMessage;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.common.security.AuthenticatedUser;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/** The organisation's view of every client's support tickets. */
@RestController
@RequestMapping("/api/v1/organisation/support-tickets")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class OrganisationSupportTicketController {

    private final SupportTicketService service;
    private final ClientRepository clientRepository;

    public OrganisationSupportTicketController(SupportTicketService service, ClientRepository clientRepository) {
        this.service = service;
        this.clientRepository = clientRepository;
    }

    @GetMapping
    public List<TicketDto> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        Function<UUID, String> clientNames = clientNameLookup();
        Function<UUID, String> clientCodes = clientCodeLookup();
        List<SupportTicket> tickets = service.listAll();
        List<UUID> ids = tickets.stream().map(SupportTicket::getId).toList();
        Map<UUID, SupportTicketMessage> latest = service.latestMessages(ids);
        Map<UUID, Boolean> unread = service.unreadByTicket(
                parseUserId(principal), SupportTicketMessage.AuthorSide.CLIENT, ids);
        return tickets.stream()
                .map(t -> TicketDto.from(t, clientNames, clientCodes, latest.get(t.getId()), unread.getOrDefault(t.getId(), false)))
                .toList();
    }

    @GetMapping("/unread-summary")
    public UnreadSummaryDto unreadSummary(@AuthenticationPrincipal AuthenticatedUser principal) {
        return new UnreadSummaryDto(service.unreadTicketCount(
                parseUserId(principal), SupportTicketMessage.AuthorSide.CLIENT, service.listAll()));
    }

    @GetMapping("/{id}/messages")
    public List<MessageDto> listMessages(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.listMessages(id, parseUserId(principal)).stream().map(MessageDto::from).toList();
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER')")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto postMessage(@PathVariable UUID id, @Valid @RequestBody MessageRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        return MessageDto.from(service.addOrganisationMessage(
                id, parseUserId(principal), actorName(principal), request.body()));
    }

    @PostMapping("/{id}/pick-up")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER')")
    public TicketDto pickUp(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        SupportTicket ticket = service.pickUp(id, principal.phoneNumber());
        service.markRead(id, parseUserId(principal));
        return toDto(ticket);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER')")
    public TicketDto resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request,
                              @AuthenticationPrincipal AuthenticatedUser principal) {
        SupportTicket ticket = service.resolve(
                id, principal.phoneNumber(), request.resolutionNotes(),
                parseUserId(principal), actorName(principal));
        service.markRead(id, parseUserId(principal));
        return toDto(ticket);
    }

    private Function<UUID, String> clientNameLookup() {
        Map<UUID, String> names = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, Client::getClientName, (a, b) -> a));
        return id -> names.getOrDefault(id, "Unknown client");
    }

    private Function<UUID, String> clientCodeLookup() {
        Map<UUID, String> codes = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, c -> c.getClientCode() != null ? c.getClientCode() : "", (a, b) -> a));
        return id -> codes.getOrDefault(id, "");
    }

    private TicketDto toDto(SupportTicket ticket) {
        return TicketDto.from(
                ticket, clientNameLookup(), clientCodeLookup(),
                service.latestMessages(List.of(ticket.getId())).get(ticket.getId()), false);
    }

    public record UnreadSummaryDto(int unreadTicketCount) {
    }

    public record ResolveRequest(@NotBlank String resolutionNotes) {
    }

    public record MessageRequest(@NotBlank String body) {
    }

    public record TicketDto(String id, String clientId, String clientName, String clientCode, String subject,
                             String description, String status, String raisedBy, String pickedUpBy, String resolvedBy,
                             String resolutionNotes, String lastMessagePreview, Instant lastMessageAt, boolean unread,
                             Instant createdAt, Instant updatedAt) {
        static TicketDto from(SupportTicket t, Function<UUID, String> clientNames, Function<UUID, String> clientCodes,
                               SupportTicketMessage last, boolean unread) {
            return new TicketDto(
                    t.getId().toString(), t.getClientId().toString(), clientNames.apply(t.getClientId()),
                    clientCodes.apply(t.getClientId()),
                    t.getSubject(), t.getDescription(), t.getStatus().name(), t.getRaisedBy(), t.getPickedUpBy(),
                    t.getResolvedBy(), t.getResolutionNotes(),
                    preview(last), last == null ? null : last.getCreatedAt(), unread,
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    public record MessageDto(String id, String ticketId, String authorSide, String authorUserId,
                              String authorName, String body, Instant createdAt) {
        static MessageDto from(SupportTicketMessage m) {
            return new MessageDto(
                    m.getId().toString(), m.getTicketId().toString(), m.getAuthorSide().name(),
                    m.getAuthorUserId() == null ? null : m.getAuthorUserId().toString(),
                    m.getAuthorName(), m.getBody(), m.getCreatedAt());
        }
    }

    private static UUID parseUserId(AuthenticatedUser principal) {
        try {
            return UUID.fromString(principal.userId());
        } catch (IllegalArgumentException ex) {
            return null;
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
