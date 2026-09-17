package com.tttn.qlnvl.warehousetransfer.repository;

import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransfer;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseTransferRepository extends JpaRepository<WarehouseTransfer, Long> {
    @EntityGraph(attributePaths = {
            "request", "request.operationType", "request.createdBy",
            "sourceWarehouse", "destinationWarehouse", "sourceConfirmedBy",
            "destinationConfirmedBy"
    })
    @Query("select transfer from WarehouseTransfer transfer where transfer.status = :status")
    Page<WarehouseTransfer> findQueue(@Param("status") WarehouseTransferStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "request", "request.operationType", "request.reason", "request.createdBy",
            "sourceWarehouse", "destinationWarehouse", "sourceConfirmedBy",
            "destinationConfirmedBy",
            "details", "details.requestDetail",
            "details.requestDetail.material"
    })
    @Query("select distinct transfer from WarehouseTransfer transfer where transfer.id = :id")
    Optional<WarehouseTransfer> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transfer from WarehouseTransfer transfer where transfer.id = :id")
    Optional<WarehouseTransfer> findByIdForUpdate(@Param("id") Long id);
}
