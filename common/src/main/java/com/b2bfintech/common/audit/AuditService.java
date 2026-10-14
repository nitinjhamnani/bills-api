package com.b2bfintech.common.audit;

import com.b2bfintech.common.security.AuthenticatedUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Writes to the current tenant's {@code audit_log} (implementation plan &sect;8.3). Called from
 * services, not controllers, so the actor is read off {@link SecurityContextHolder} rather than
 * passed by every caller - falls back to "SYSTEM" for actions with no authenticated caller (e.g.
 * the scheduled {@code BillAutoFetchJob}).
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String action, String entityType, String entityId, String details) {
        AuditLog entry = new AuditLog();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            entry.setActorUserId(user.phoneNumber());
            entry.setActorRole(user.role());
        } else {
            entry.setActorUserId("SYSTEM");
            entry.setActorRole("SYSTEM");
        }
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(details);
        repository.save(entry);
    }
}
