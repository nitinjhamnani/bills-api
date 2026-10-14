package com.b2bfintech.common.audit;

import com.b2bfintech.common.exception.BadRequestException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Read-only audit trail for the current tenant (implementation plan &sect;8.3). */
@RestController
@RequestMapping("/api/v1/organisation/audit-log")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class AuditLogController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_RANGE_DAYS = 90;

    private final AuditLogRepository repository;

    public AuditLogController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<EntryDto> list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        if ((from == null || from.isBlank()) && (to == null || to.isBlank())) {
            return repository.findAllByOrderByCreatedAtDesc().stream().map(EntryDto::from).toList();
        }
        DateRange range = parseRange(from, to);
        return repository.findByCreatedAtBetweenOrderByCreatedAtDesc(range.from, range.to)
                .stream().map(EntryDto::from).toList();
    }

    private static DateRange parseRange(String from, String to) {
        try {
            LocalDate endDate = to != null && !to.isBlank() ? LocalDate.parse(to) : LocalDate.now(IST);
            LocalDate startDate = from != null && !from.isBlank() ? LocalDate.parse(from) : endDate.minusDays(30);
            if (startDate.isAfter(endDate)) {
                throw new BadRequestException("'from' must be before 'to'");
            }
            long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            if (days > MAX_RANGE_DAYS) {
                throw new BadRequestException("Date range cannot exceed " + MAX_RANGE_DAYS + " days");
            }
            return new DateRange(
                    startDate.atStartOfDay(IST).toInstant(),
                    endDate.plusDays(1).atStartOfDay(IST).toInstant());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("from/to must be dates in yyyy-MM-dd format");
        }
    }

    public record EntryDto(String actorUserId, String actorRole, String action, String entityType,
                            String entityId, String details, Instant createdAt) {
        static EntryDto from(AuditLog log) {
            return new EntryDto(log.getActorUserId(), log.getActorRole(), log.getAction(),
                    log.getEntityType(), log.getEntityId(), log.getDetails(), log.getCreatedAt());
        }
    }

    private record DateRange(Instant from, Instant to) {
    }
}
