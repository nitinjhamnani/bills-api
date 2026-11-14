package com.b2bfintech.billing.repository;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillFetch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BillFetchRepository extends JpaRepository<BillFetch, UUID> {

    @Query("""
            select f from Bill b join b.latestFetch f
            where b.sourceType = :sourceType
              and b.sourceId = :sourceId
              and f.billPaymentStatus = :paymentStatus
              and f.fetchStatus = :fetchStatus
              and f.billAmount > 0
            order by f.fetchedOn desc
            """)
    List<BillFetch> findLatestBySource(
            @Param("sourceType") Bill.SourceType sourceType,
            @Param("sourceId") String sourceId,
            @Param("paymentStatus") BillFetch.PaymentStatus paymentStatus,
            @Param("fetchStatus") BillFetch.FetchStatus fetchStatus);

    /** Each bill's latest fetch that is still waiting for the corporate to pay. */
    default List<BillFetch> findDues(String corpId) {
        return findLatestBySource(Bill.SourceType.CORP, corpId,
                BillFetch.PaymentStatus.PENDING, BillFetch.FetchStatus.SUCCESS);
    }
}
