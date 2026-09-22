package com.tttn.qlnvl.inventory.repository;

import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {
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
                   lot.condition as condition,
                   reason.name as reasonName,
                   lot.unitPrice as unitPrice,
                   requestDetail.quantity as quantity,
                   purchaseOrder.poCode as purchaseOrderCode,
                   lot.id as layerId
            from InventoryLot lot
            join lot.receiptTransactionDetail detail
            join detail.transaction transaction
            join detail.requestDetail requestDetail
            join transaction.request request
            join request.operationType operationType
            join request.reason reason
            join lot.warehouse warehouse
            join lot.material material
            left join lot.sourcePurchaseOrderItem poItem
            left join poItem.purchaseOrder purchaseOrder
            where transaction.status = com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus.COMPLETED
              and operationType.direction = com.tttn.qlnvl.warehouserequest.domain.OperationDirection.IMPORT
              and warehouse.id = :warehouseId
              and material.materialGroup.id = :materialGroupId
              and (:materialId is null or material.id = :materialId)
              and lot.condition in :conditions
              and transaction.physicalConfirmedAt >= :fromInclusive
              and transaction.physicalConfirmedAt < :toExclusive
            """)
    List<StockCardMovementProjection> findStockCardReceipts(
            @Param("warehouseId") Long warehouseId,
            @Param("materialGroupId") Long materialGroupId,
            @Param("materialId") Long materialId,
            @Param("conditions") List<MaterialCondition> conditions,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            select transfer.destinationConfirmedAt as transactionDate,
                   request.requestCode as documentCode,
                   operationType.name as operationTypeName,
                   warehouse.id as warehouseId,
                   warehouse.warehouseCode as warehouseCode,
                   warehouse.warehouseName as warehouseName,
                   material.id as materialId,
                   material.materialCode as materialCode,
                   material.materialName as materialName,
                   lot.condition as condition,
                   reason.name as reasonName,
                   lot.unitPrice as unitPrice,
                   allocation.allocatedQuantity as quantity,
                   purchaseOrder.poCode as purchaseOrderCode,
                   lot.id as layerId
            from InventoryLot lot
            join lot.sourceTransferAllocation allocation
            join allocation.transferDetail detail
            join detail.transfer transfer
            join transfer.request request
            join request.operationType operationType
            join request.reason reason
            join lot.warehouse warehouse
            join lot.material material
            left join lot.sourcePurchaseOrderItem poItem
            left join poItem.purchaseOrder purchaseOrder
            where transfer.status = com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus.COMPLETED
              and warehouse.id = :warehouseId
              and material.materialGroup.id = :materialGroupId
              and (:materialId is null or material.id = :materialId)
              and lot.condition in :conditions
              and transfer.destinationConfirmedAt >= :fromInclusive
              and transfer.destinationConfirmedAt < :toExclusive
            """)
    List<StockCardMovementProjection> findStockCardTransferReceipts(
            @Param("warehouseId") Long warehouseId,
            @Param("materialGroupId") Long materialGroupId,
            @Param("materialId") Long materialId,
            @Param("conditions") List<MaterialCondition> conditions,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @EntityGraph(attributePaths = {"warehouse", "material", "material.materialGroup",
            "sourcePurchaseOrderItem", "sourcePurchaseOrderItem.purchaseOrder"})
    @Query(value = """
            select lot from InventoryLot lot
            where lot.warehouse.status = com.tttn.qlnvl.warehouse.domain.WarehouseStatus.ACTIVE
              and lot.material.status = com.tttn.qlnvl.material.domain.MaterialStatus.ACTIVE
              and lot.material.materialGroup.active = true
              and (:warehouseId is null or lot.warehouse.id = :warehouseId)
              and (:materialGroupId is null or lot.material.materialGroup.id = :materialGroupId)
              and (:materialId is null or lot.material.id = :materialId)
              and lot.condition in :conditions
            order by lot.material.materialCode, lot.receivedAt, lot.id
            """,
            countQuery = """
            select count(lot) from InventoryLot lot
            where lot.warehouse.status = com.tttn.qlnvl.warehouse.domain.WarehouseStatus.ACTIVE
              and lot.material.status = com.tttn.qlnvl.material.domain.MaterialStatus.ACTIVE
              and lot.material.materialGroup.active = true
              and (:warehouseId is null or lot.warehouse.id = :warehouseId)
              and (:materialGroupId is null or lot.material.materialGroup.id = :materialGroupId)
              and (:materialId is null or lot.material.id = :materialId)
              and lot.condition in :conditions
            """)
    Page<InventoryLot> searchDetailedInventory(@Param("warehouseId") Long warehouseId,
            @Param("materialGroupId") Long materialGroupId,
            @Param("materialId") Long materialId,
            @Param("conditions") List<MaterialCondition> conditions,
            Pageable pageable);

    @Query("""
            select lot.warehouse as warehouse,
                   lot.material as material,
                   lot.condition as condition,
                   sum(lot.onHandQuantity) as onHandQuantity,
                   sum(lot.reservedQuantity) as lotReservedQuantity
            from InventoryLot lot
            where lot.warehouse.status = com.tttn.qlnvl.warehouse.domain.WarehouseStatus.ACTIVE
              and lot.material.status = com.tttn.qlnvl.material.domain.MaterialStatus.ACTIVE
              and lot.material.materialGroup.active = true
              and (:warehouseId is null or lot.warehouse.id = :warehouseId)
              and (:materialGroupId is null or lot.material.materialGroup.id = :materialGroupId)
              and (:materialId is null or lot.material.id = :materialId)
              and lot.condition in :conditions
            group by lot.warehouse, lot.material, lot.condition
            order by lot.material.materialCode, lot.warehouse.warehouseCode, lot.condition
            """)
    List<DetailedInventoryAggregate> aggregateDetailedInventory(
            @Param("warehouseId") Long warehouseId,
            @Param("materialGroupId") Long materialGroupId,
            @Param("materialId") Long materialId,
            @Param("conditions") List<MaterialCondition> conditions);
    @Query("""
            select lot.warehouse as warehouse,
                   lot.material as material,
                   lot.condition as condition,
                   sum(lot.onHandQuantity) as onHandQuantity,
                   sum(lot.reservedQuantity) as lotReservedQuantity
            from InventoryLot lot
            group by lot.warehouse, lot.material, lot.condition
            """)
    List<DailyLotAggregate> aggregateForDailySnapshot();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lot from InventoryLot lot where lot.id in :ids order by lot.id")
    List<InventoryLot> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select lot from InventoryLot lot
            where lot.warehouse.id = :warehouseId
              and lot.material.id = :materialId
              and lot.condition = :condition
            order by lot.id
            """)
    List<InventoryLot> findDimensionForUpdate(@Param("warehouseId") Long warehouseId,
            @Param("materialId") Long materialId,
            @Param("condition") MaterialCondition condition);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select lot from InventoryLot lot
            where lot.warehouse.id = :warehouseId
              and lot.material.id = :materialId
              and lot.condition = :condition
              and lot.onHandQuantity > lot.reservedQuantity
            order by lot.receivedAt, lot.id
            """)
    List<InventoryLot> findAvailableFifoForUpdate(@Param("warehouseId") Long warehouseId,
            @Param("materialId") Long materialId,
            @Param("condition") MaterialCondition condition);

    interface DailyLotAggregate {
        Warehouse getWarehouse();
        Material getMaterial();
        MaterialCondition getCondition();
        long getOnHandQuantity();
        long getLotReservedQuantity();
    }

    interface DetailedInventoryAggregate {
        Warehouse getWarehouse();
        Material getMaterial();
        MaterialCondition getCondition();
        long getOnHandQuantity();
        long getLotReservedQuantity();
    }
}
