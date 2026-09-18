package com.tttn.qlnvl.inventory.repository;

import com.tttn.qlnvl.inventory.domain.InventoryDailySnapshot;
import java.time.LocalDate;
import java.util.List;
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
}
