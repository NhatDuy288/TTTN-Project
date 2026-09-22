package com.tttn.qlnvl.warehousetransfer.repository;

import com.tttn.qlnvl.inventory.repository.StockCardMovementProjection;
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

    @Query("""
            select transfer.sourceConfirmedAt as transactionDate,
                   request.requestCode as documentCode,
                   operationType.name as operationTypeName,
                   warehouse.id as warehouseId,
                   warehouse.warehouseCode as warehouseCode,
                   warehouse.warehouseName as warehouseName,
                   material.id as materialId,
                   material.materialCode as materialCode,
                   material.materialName as materialName,
                   requestDetail.condition as condition,
                   reason.name as reasonName,
                   allocation.allocatedUnitPrice as unitPrice,
                   allocation.allocatedQuantity as quantity,
                   purchaseOrder.poCode as purchaseOrderCode,
                   allocation.id as layerId
            from TransferLotAllocation allocation
            join allocation.transferDetail detail
            join detail.transfer transfer
            join detail.requestDetail requestDetail
            join transfer.request request
            join request.operationType operationType
            join request.reason reason
            join transfer.sourceWarehouse warehouse
            join requestDetail.material material
            join allocation.inventoryLot lot
            left join lot.sourcePurchaseOrderItem poItem
            left join poItem.purchaseOrder purchaseOrder
            where transfer.status in (
                    com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus.IN_TRANSIT,
                    com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus.COMPLETED)
              and warehouse.id = :warehouseId
              and material.materialGroup.id = :materialGroupId
              and (:materialId is null or material.id = :materialId)
              and requestDetail.condition in :conditions
              and transfer.sourceConfirmedAt >= :fromInclusive
              and transfer.sourceConfirmedAt < :toExclusive
            """)
    List<StockCardMovementProjection> findStockCardTransferIssues(
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
