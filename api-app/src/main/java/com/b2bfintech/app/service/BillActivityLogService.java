package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillPaymentLogRepository;
import com.b2bfintech.organisation.repository.AppUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Writes/reads a bill's full activity trail - creation, every fetch outcome, client payment, and
 * every organisation-side action - each a separately timestamped {@link BillPaymentLog} row. */
@Service
public class BillActivityLogService {

    private final BillPaymentLogRepository repository;
    private final AppUserRepository appUserRepository;

    public BillActivityLogService(BillPaymentLogRepository repository, AppUserRepository appUserRepository) {
        this.repository = repository;
        this.appUserRepository = appUserRepository;
    }

    public void log(UUID billId, UUID billPaymentId, BillPaymentLog.EventType eventType, String description, String actor) {
        String signature = sign(actor);
        String text = description;
        if (text != null && actor != null && signature != null && !actor.equals(signature)) {
            text = text.replace(actor, signature);
        }
        BillPaymentLog entry = new BillPaymentLog();
        entry.setBillId(billId);
        entry.setBillPaymentId(billPaymentId);
        entry.setEventType(eventType);
        entry.setDescription(text);
        entry.setActor(signature);
        repository.save(entry);
    }

    /** A phone number is stored as that user's code. System labels and codes already issued are kept. */
    private String sign(String actor) {
        if (actor == null || actor.isBlank()) {
            return actor;
        }
        return appUserRepository.findByPhoneNumber(actor).map(user -> user.getUserCode()).orElse(actor);
    }

    public List<BillPaymentLog> listForBill(UUID billId) {
        return repository.findByBillIdOrderByCreatedAtDesc(billId);
    }

    /** Just the events for one specific fetch/payment cycle - what the "Bills against this
     * account" list's per-row detail page shows, as opposed to the whole bill's full history. */
    public List<BillPaymentLog> listForBillPayment(UUID billPaymentId) {
        return repository.findByBillPaymentIdOrderByCreatedAtDesc(billPaymentId);
    }
}
