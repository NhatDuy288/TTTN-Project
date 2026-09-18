package com.tttn.qlnvl.inventory.application;

import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.domain.MaterialStatus;
import com.tttn.qlnvl.material.domain.MaterialUnit;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.domain.WarehouseStatus;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.repository.StockReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryReportService {
    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(20, 50, 100);

    private final InventoryLotRepository lotRepository;
    private final StockReservationRepository reservationRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialGroupRepository materialGroupRepository;
    private final MaterialRepository materialRepository;

    public InventoryReportService(InventoryLotRepository lotRepository,
            StockReservationRepository reservationRepository,
            WarehouseRepository warehouseRepository,
            MaterialGroupRepository materialGroupRepository,
            MaterialRepository materialRepository) {
        this.lotRepository = lotRepository;
        this.reservationRepository = reservationRepository;
        this.warehouseRepository = warehouseRepository;
        this.materialGroupRepository = materialGroupRepository;
        this.materialRepository = materialRepository;
    }

    @Transactional(readOnly = true)
    public DetailedInventoryReport detailedInventory(Long warehouseId, Long materialGroupId,
            Long materialId, List<MaterialCondition> selectedConditions, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 20;
        List<MaterialCondition> conditions = normalizedConditions(selectedConditions);

        Page<LotRow> lots = lotRepository.searchDetailedInventory(warehouseId, materialGroupId,
                        materialId, conditions, PageRequest.of(safePage, safeSize))
                .map(this::lotRow);

        Map<DimensionKey, MutableAggregate> totals = new LinkedHashMap<>();
        lotRepository.aggregateDetailedInventory(warehouseId, materialGroupId, materialId,
                conditions).forEach(row -> totals.put(
                        key(row.getWarehouse(), row.getMaterial(), row.getCondition()),
                        new MutableAggregate(row.getWarehouse(), row.getMaterial(),
                                row.getCondition(), row.getOnHandQuantity(),
                                row.getLotReservedQuantity(), 0)));
        reservationRepository.aggregateDetailedInventorySoftReservations(warehouseId,
                materialGroupId, materialId, conditions).forEach(row -> {
                    DimensionKey key = key(row.getWarehouse(), row.getMaterial(),
                            row.getCondition());
                    totals.compute(key, (ignored, current) -> current == null
                            ? new MutableAggregate(row.getWarehouse(), row.getMaterial(),
                                    row.getCondition(), 0, 0,
                                    row.getSoftReservedQuantity())
                            : current.withSoftReserved(row.getSoftReservedQuantity()));
                });

        List<AggregateRow> aggregates = totals.values().stream()
                .map(MutableAggregate::toRow)
                .sorted((left, right) -> {
                    int material = left.materialCode().compareTo(right.materialCode());
                    if (material != 0) return material;
                    int warehouse = left.warehouseCode().compareTo(right.warehouseCode());
                    if (warehouse != 0) return warehouse;
                    return left.condition().compareTo(right.condition());
                })
                .toList();
        return new DetailedInventoryReport(lots, aggregates);
    }

    @Transactional(readOnly = true)
    public List<Warehouse> activeWarehouses() {
        return warehouseRepository.findByStatusOrderByWarehouseCodeAsc(WarehouseStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<MaterialGroup> activeMaterialGroups() {
        return materialGroupRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<Material> activeMaterials() {
        return materialRepository.findByStatusOrderByMaterialCodeAsc(MaterialStatus.ACTIVE);
    }

    private List<MaterialCondition> normalizedConditions(List<MaterialCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of(MaterialCondition.values());
        }
        return new ArrayList<>(EnumSet.copyOf(conditions));
    }

    private LotRow lotRow(InventoryLot lot) {
        String poCode = lot.getSourcePurchaseOrderItem() == null ? null
                : lot.getSourcePurchaseOrderItem().getPurchaseOrder().getPoCode();
        return new LotRow(lot.getId(), lot.getMaterial().getMaterialCode(),
                lot.getMaterial().getMaterialName(), lot.getMaterial().getUnit(),
                lot.getCondition(), lot.getWarehouse().getWarehouseCode(),
                lot.getWarehouse().getWarehouseName(), lot.getOnHandQuantity(),
                lot.getReservedQuantity(), lot.getOnHandQuantity() - lot.getReservedQuantity(),
                lot.getUnitPrice(), poCode, lot.getReceivedAt());
    }

    private DimensionKey key(Warehouse warehouse, Material material,
            MaterialCondition condition) {
        return new DimensionKey(warehouse.getId(), material.getId(), condition);
    }

    public record DetailedInventoryReport(Page<LotRow> lots, List<AggregateRow> aggregates) {}

    public record LotRow(Long lotId, String materialCode, String materialName, MaterialUnit unit,
                         MaterialCondition condition, String warehouseCode, String warehouseName,
                         long onHand, long lotReserved, long lotAvailable, BigDecimal unitPrice,
                         String poCode, Instant receivedAt) {}

    public record AggregateRow(String materialCode, String materialName, MaterialUnit unit,
                               MaterialCondition condition, String warehouseCode,
                               String warehouseName, long onHand, long lotReserved,
                               long softReserved, long actualAvailable) {}

    private record DimensionKey(Long warehouseId, Long materialId,
                                MaterialCondition condition) {}

    private record MutableAggregate(Warehouse warehouse, Material material,
                                    MaterialCondition condition, long onHand,
                                    long lotReserved, long softReserved) {
        MutableAggregate withSoftReserved(long quantity) {
            return new MutableAggregate(warehouse, material, condition, onHand, lotReserved,
                    quantity);
        }

        AggregateRow toRow() {
            return new AggregateRow(material.getMaterialCode(), material.getMaterialName(),
                    material.getUnit(), condition, warehouse.getWarehouseCode(),
                    warehouse.getWarehouseName(), onHand, lotReserved, softReserved,
                    onHand - lotReserved - softReserved);
        }
    }
}