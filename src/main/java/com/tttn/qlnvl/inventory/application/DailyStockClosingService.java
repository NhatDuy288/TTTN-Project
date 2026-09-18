package com.tttn.qlnvl.inventory.application;

import com.tttn.qlnvl.inventory.domain.InventoryDailySnapshot;
import com.tttn.qlnvl.inventory.repository.InventoryDailySnapshotRepository;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.repository.StockReservationRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyStockClosingService {
    private final InventoryLotRepository lotRepository;
    private final StockReservationRepository reservationRepository;
    private final InventoryDailySnapshotRepository snapshotRepository;

    public DailyStockClosingService(InventoryLotRepository lotRepository,
            StockReservationRepository reservationRepository,
            InventoryDailySnapshotRepository snapshotRepository) {
        this.lotRepository = lotRepository;
        this.reservationRepository = reservationRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public int close(LocalDate snapshotDate) {
        snapshotRepository.lockForClosing();
        Map<DimensionKey, Totals> totals = new LinkedHashMap<>();
        lotRepository.aggregateForDailySnapshot().forEach(row -> {
            DimensionKey key = key(row.getWarehouse(), row.getMaterial(), row.getCondition());
            totals.put(key, new Totals(row.getWarehouse(), row.getMaterial(),
                    row.getCondition(), row.getOnHandQuantity(),
                    row.getLotReservedQuantity(), 0));
        });
        reservationRepository.aggregateActiveSoftForDailySnapshot().forEach(row -> {
            DimensionKey key = key(row.getWarehouse(), row.getMaterial(), row.getCondition());
            totals.compute(key, (ignored, current) -> current == null
                    ? new Totals(row.getWarehouse(), row.getMaterial(), row.getCondition(),
                            0, 0, row.getSoftReservedQuantity())
                    : current.withSoftReserved(row.getSoftReservedQuantity()));
        });

        Map<DimensionKey, InventoryDailySnapshot> existing = new LinkedHashMap<>();
        snapshotRepository.findAllBySnapshotDate(snapshotDate).forEach(snapshot ->
                existing.put(key(snapshot.getWarehouse(), snapshot.getMaterial(),
                        snapshot.getCondition()), snapshot));

        List<InventoryDailySnapshot> snapshots = totals.entrySet().stream().map(entry -> {
            Totals value = entry.getValue();
            long reserved = value.lotReserved() + value.softReserved();
            InventoryDailySnapshot snapshot = existing.get(entry.getKey());
            if (snapshot == null) {
                return new InventoryDailySnapshot(snapshotDate, value.warehouse(), value.material(),
                        value.condition(), value.onHand(), reserved);
            }
            snapshot.updateQuantities(value.onHand(), reserved);
            return snapshot;
        }).toList();
        snapshotRepository.saveAllAndFlush(snapshots);
        return snapshots.size();
    }

    private DimensionKey key(Warehouse warehouse, Material material,
            MaterialCondition condition) {
        return new DimensionKey(warehouse.getId(), material.getId(), condition);
    }

    private record DimensionKey(Long warehouseId, Long materialId,
                                MaterialCondition condition) {}

    private record Totals(Warehouse warehouse, Material material, MaterialCondition condition,
                          long onHand, long lotReserved, long softReserved) {
        Totals withSoftReserved(long quantity) {
            return new Totals(warehouse, material, condition, onHand, lotReserved, quantity);
        }
    }
}
