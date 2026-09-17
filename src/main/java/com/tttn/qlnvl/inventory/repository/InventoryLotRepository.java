package com.tttn.qlnvl.inventory.repository;

import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {
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
}
