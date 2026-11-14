package com.b2bfintech.billing.repository;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillFetch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, String> {

    @Query("select b from Bill b where b.sourceId = :clientId order by b.billId desc")
    List<Bill> findByClientIdOrderByCreatedAtDesc(@Param("clientId") String clientId);

    List<Bill> findAllByOrderByBillIdDesc();

    @Query("select b from Bill b where b.inputParamJson is not null")
    List<Bill> findByAutoFetchEnabledTrue();

    Optional<Bill> findFirstBySourceIdAndCategoryNameAndOperatorNameAndPrimaryParamValue(
            String sourceId, String categoryName, String operatorName, String primaryParamValue);

    @Query("select count(b) from Bill b where b.latestFetch.fetchStatus = :status")
    long countByFetchStatus(@Param("status") BillFetch.FetchStatus status);

    @Query("select count(b) from Bill b where b.sourceId = :clientId and b.latestFetch.billPaymentStatus = :status")
    long countByClientIdAndFetchPaymentStatus(@Param("clientId") String clientId,
                                               @Param("status") BillFetch.PaymentStatus status);

    @Query("""
            select count(b) from Bill b
            where b.sourceId = :clientId
              and b.latestFetch.billStatus = :status
            """)
    long countByClientIdAndFetchStatus(@Param("clientId") String clientId,
                                        @Param("status") BillFetch.BillStatus status);

    @Query("select count(b) from Bill b where b.sourceId = :clientId")
    long countByClientId(@Param("clientId") String clientId);

    @Query("select coalesce(sum(f.billAmount), 0) from Bill b join b.latestFetch f where b.sourceId = :clientId and f.billStatus = :status")
    BigDecimal sumAmountByClientIdAndFetchStatus(@Param("clientId") String clientId,
                                                  @Param("status") BillFetch.BillStatus status);

    @Query("select coalesce(sum(f.billAmount), 0) from Bill b join b.latestFetch f where b.sourceId = :clientId and f.billPaymentStatus = :status")
    BigDecimal sumAmountByClientIdAndFetchPaymentStatus(@Param("clientId") String clientId,
                                                         @Param("status") BillFetch.PaymentStatus status);

    @Query("select coalesce(sum(f.billAmount), 0) from Bill b join b.latestFetch f where b.sourceId = :clientId")
    BigDecimal sumAmountByClientId(@Param("clientId") String clientId);

    @Query("select b.branchId as branchId, count(b) as billCount from Bill b where b.sourceId = :clientId group by b.branchId")
    List<BranchBillCount> countGroupedByBranch(@Param("clientId") String clientId);

    interface BranchBillCount {
        String getBranchId();
        long getBillCount();
    }
}
