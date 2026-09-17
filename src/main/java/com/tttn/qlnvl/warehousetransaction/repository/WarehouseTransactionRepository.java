package com.tttn.qlnvl.warehousetransaction.repository;

import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseTransactionRepository extends JpaRepository<WarehouseTransaction, Long> {
    @EntityGraph(attributePaths = {
            "request", "request.operationType", "request.createdBy"
    })
    @Query("select transaction from WarehouseTransaction transaction where transaction.status = :status")
    Page<WarehouseTransaction> findQueue(@Param("status") WarehouseTransactionStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "request", "request.operationType", "request.reason", "request.sourceWarehouse",
            "request.destinationWarehouse", "request.purchaseOrder", "request.createdBy",
            "details", "details.requestDetail", "details.requestDetail.material",
            "details.requestDetail.purchaseOrderItem"
    })
    @Query("select transaction from WarehouseTransaction transaction where transaction.id = :id")
    Optional<WarehouseTransaction> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transaction from WarehouseTransaction transaction where transaction.id = :id")
    Optional<WarehouseTransaction> findByIdForUpdate(@Param("id") Long id);
}
