package com.tttn.qlnvl.inventory.repository;

import com.tttn.qlnvl.inventory.domain.InventoryDailySnapshot;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryDailySnapshotRepository
        extends JpaRepository<InventoryDailySnapshot, Long> {
    @Modifying
    @Query(value = "LOCK TABLE inventory_daily_snapshot IN SHARE ROW EXCLUSIVE MODE",
            nativeQuery = true)
    void lockForClosing();

    @Query("select snapshot from InventoryDailySnapshot snapshot where snapshot.snapshotDate = :date")
    List<InventoryDailySnapshot> findAllBySnapshotDate(@Param("date") LocalDate date);

    @EntityGraph(attributePaths = {"warehouse", "material", "material.materialGroup"})
    @Query("""
            select snapshot from InventoryDailySnapshot snapshot
            where snapshot.snapshotDate = :date
              and snapshot.warehouse.id = :warehouseId
              and (:materialGroupId is null
                   or snapshot.material.materialGroup.id = :materialGroupId)
              and (:materialId is null or snapshot.material.id = :materialId)
              and snapshot.condition in :conditions
            order by snapshot.material.materialCode, snapshot.condition
            """)
    List<InventoryDailySnapshot> findNxtOpening(@Param("date") LocalDate date,
            @Param("warehouseId") Long warehouseId,
            @Param("materialGroupId") Long materialGroupId,
            @Param("materialId") Long materialId,
            @Param("conditions") List<MaterialCondition> conditions);
}
