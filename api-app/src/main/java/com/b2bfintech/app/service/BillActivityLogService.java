package com.b2bfintech.app.service;

import com.b2bfintech.billing.entity.BillPaymentLog;
import com.b2bfintech.billing.repository.BillPaymentLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Writes/reads a bill's full activity trail - creation, every fetch outcome, client payment, and
 * every organisation-side action - each a separately timestamped {@link BillPaymentLog} row. */
@Service
public class BillActivityLogService {

    private final BillPaymentLogRepository repository;

    public BillActivityLogService(BillPaymentLogRepository repository) {
        this.repository = repository;
    }

    public void log(UUID billId, UUID billPaymentId, BillPaymentLog.EventType eventType, String description, String actor) {
        BillPaymentLog entry = new BillPaymentLog();
        entry.setBillId(billId);
        entry.setBillPaymentId(billPaymentId);
        entry.setEventType(eventType);
        entry.setDescription(description);
        entry.setActor(actor);
        repository.save(entry);
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
