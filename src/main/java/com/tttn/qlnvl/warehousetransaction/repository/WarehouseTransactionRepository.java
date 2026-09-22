package com.tttn.qlnvl.warehousetransaction.repository;

import com.tttn.qlnvl.inventory.repository.StockCardMovementProjection;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
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

public interface WarehouseTransactionRepository extends JpaRepository<WarehouseTransaction, Long> {
    @EntityGraph(attributePaths = {
            "request", "request.operationType", "request.createdBy"
    })
    @Query("select transaction from WarehouseTransaction transaction where transaction.status = :status")
    Page<WarehouseTransaction> findQueue(@Param("status") WarehouseTransactionStatus status,
            Pageable pageable);

    @Query("""
            select request.destinationWarehouse as warehouse,
                   requestDetail.material as material,
                   requestDetail.condition as condition,
                   sum(requestDetail.quantity) as quantity
            from WarehouseTransaction transaction
            join transaction.request request
            join transaction.details detail
            join detail.requestDetail requestDetail
            where transaction.status = com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus.COMPLETED
              and request.operationType.direction = com.tttn.qlnvl.warehouserequest.domain.OperationDirection.IMPORT
              and request.destinationWarehouse.id = :warehouseId
              and transaction.physicalConfirmedAt >= :fromInclusive
              and transaction.physicalConfirmedAt < :toExclusive
              and (:materialGroupId is null
                   or requestDetail.material.materialGroup.id = :materialGroupId)
              and (:materialId is null or requestDetail.material.id = :materialId)
              and requestDetail.condition in :conditions
            group by request.destinationWarehouse, requestDetail.material,
                     requestDetail.condition
            """)
    List<NxtTransactionAggregate> aggregateNxtImports(
            @Param("warehouseId") Long warehouseId,
            @Param("materialGroupId") Long materialGroupId,
            @Param("materialId") Long materialId,
            @Param("conditions") List<MaterialCondition> conditions,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            select request.sourceWarehouse as warehouse,
                   requestDetail.material as material,
                   requestDetail.condition as condition,
                   sum(requestDetail.quantity) as quantity
            from WarehouseTransaction transaction
            join transaction.request request
            join transaction.details detail
            join detail.requestDetail requestDetail
            where transaction.status = com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus.COMPLETED
              and request.operationType.direction = com.tttn.qlnvl.warehouserequest.domain.OperationDirection.EXPORT
              and request.sourceWarehouse.id = :warehouseId
              and transaction.physicalConfirmedAt >= :fromInclusive
              and transaction.physicalConfirmedAt < :toExclusive
              and (:materialGroupId is null
                   or requestDetail.material.materialGroup.id = :materialGroupId)
              and (:materialId is null or requestDetail.material.id = :materialId)
              and requestDetail.condition in :conditions
            group by request.sourceWarehouse, requestDetail.material,
                     requestDetail.condition
            """)
    List<NxtTransactionAggregate> aggregateNxtExports(
            @Param("warehouseId") Long warehouseId,
            @Param("materialGroupId") Long materialGroupId,
            @Param("materialId") Long materialId,
            @Param("conditions") List<MaterialCondition> conditions,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            select transaction.physicalConfirmedAt as transactionDate,
                   transaction.transactionCode as documentCode,
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
            from IssueLotAllocation allocation
            join allocation.transactionDetail detail
            join detail.transaction transaction
            join detail.requestDetail requestDetail
            join transaction.request request
            join request.operationType operationType
            join request.reason reason
            join request.sourceWarehouse warehouse
            join requestDetail.material material
            join allocation.inventoryLot lot
            left join lot.sourcePurchaseOrderItem poItem
            left join poItem.purchaseOrder purchaseOrder
            where transaction.status = com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus.COMPLETED
              and operationType.direction = com.tttn.qlnvl.warehouserequest.domain.OperationDirection.EXPORT
              and warehouse.id = :warehouseId
              and material.materialGroup.id = :materialGroupId
              and (:materialId is null or material.id = :materialId)
              and requestDetail.condition in :conditions
              and transaction.physicalConfirmedAt >= :fromInclusive
              and transaction.physicalConfirmedAt < :toExclusive
            """)
    List<StockCardMovementProjection> findStockCardIssues(
            @Param("warehouseId") Long warehouseId,
            @Param("materialGroupId") Long materialGroupId,
            @Param("materialId") Long materialId,
            @Param("conditions") List<MaterialCondition> conditions,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

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

    interface NxtTransactionAggregate {
        Warehouse getWarehouse();
        Material getMaterial();
        MaterialCondition getCondition();
        long getQuantity();
    }
}
