package com.tttn.qlnvl.warehousetransfer.repository;

import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransfer;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
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

    @Query("""
            select transfer.sourceWarehouse as warehouse,
                   requestDetail.material as material,
                   requestDetail.condition as condition,
                   sum(requestDetail.quantity) as quantity
            from WarehouseTransfer transfer
            join transfer.details detail
            join detail.requestDetail requestDetail
            where transfer.sourceWarehouse.id = :warehouseId
              and transfer.sourceConfirmedAt >= :fromInclusive
              and transfer.sourceConfirmedAt < :toExclusive
              and (:materialGroupId is null
                   or requestDetail.material.materialGroup.id = :materialGroupId)
              and (:materialId is null or requestDetail.material.id = :materialId)
              and requestDetail.condition in :conditions
            group by transfer.sourceWarehouse, requestDetail.material,
                     requestDetail.condition
            """)
    List<NxtTransferAggregate> aggregateNxtSourceExports(
            @Param("warehouseId") Long warehouseId,
            @Param("materialGroupId") Long materialGroupId,
            @Param("materialId") Long materialId,
            @Param("conditions") List<MaterialCondition> conditions,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            select transfer.destinationWarehouse as warehouse,
                   requestDetail.material as material,
                   requestDetail.condition as condition,
                   sum(requestDetail.quantity) as quantity
            from WarehouseTransfer transfer
            join transfer.details detail
            join detail.requestDetail requestDetail
            where transfer.destinationWarehouse.id = :warehouseId
              and transfer.destinationConfirmedAt >= :fromInclusive
              and transfer.destinationConfirmedAt < :toExclusive
              and (:materialGroupId is null
                   or requestDetail.material.materialGroup.id = :materialGroupId)
              and (:materialId is null or requestDetail.material.id = :materialId)
              and requestDetail.condition in :conditions
            group by transfer.destinationWarehouse, requestDetail.material,
                     requestDetail.condition
            """)
    List<NxtTransferAggregate> aggregateNxtDestinationImports(
            @Param("warehouseId") Long warehouseId,
            @Param("materialGroupId") Long materialGroupId,
            @Param("materialId") Long materialId,
            @Param("conditions") List<MaterialCondition> conditions,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

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

    interface NxtTransferAggregate {
        Warehouse getWarehouse();
        Material getMaterial();
        MaterialCondition getCondition();
        long getQuantity();
    }
}
