package com.tttn.qlnvl.inventory.application;

import com.tttn.qlnvl.inventory.domain.InventoryDailySnapshot;
import com.tttn.qlnvl.inventory.repository.InventoryDailySnapshotRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.domain.MaterialUnit;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NxtReportService {
    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(20, 50, 100);

    private final InventoryDailySnapshotRepository snapshotRepository;
    private final WarehouseTransactionRepository transactionRepository;
    private final WarehouseTransferRepository transferRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialGroupRepository materialGroupRepository;
    private final MaterialRepository materialRepository;
    private final Clock businessClock;

    public NxtReportService(InventoryDailySnapshotRepository snapshotRepository,
            WarehouseTransactionRepository transactionRepository,
            WarehouseTransferRepository transferRepository,
            WarehouseRepository warehouseRepository,
            MaterialGroupRepository materialGroupRepository,
            MaterialRepository materialRepository,
            Clock businessClock) {
        this.snapshotRepository = snapshotRepository;
        this.transactionRepository = transactionRepository;
        this.transferRepository = transferRepository;
        this.warehouseRepository = warehouseRepository;
        this.materialGroupRepository = materialGroupRepository;
        this.materialRepository = materialRepository;
        this.businessClock = businessClock;
    }

    public LocalDate defaultToDate() {
        return LocalDate.now(businessClock);
    }

    public LocalDate defaultFromDate() {
        return defaultToDate().minusDays(7);
    }

    @Transactional(readOnly = true)
    public NxtReport report(Long warehouseId, Long materialGroupId, Long materialId,
            List<MaterialCondition> selectedConditions, LocalDate fromDate, LocalDate toDate,
            int page, int size) {
        validate(warehouseId, fromDate, toDate);
        List<MaterialCondition> conditions = normalizedConditions(selectedConditions);
        Instant fromInclusive = fromDate.atStartOfDay(businessClock.getZone()).toInstant();
        Instant toExclusive = toDate.plusDays(1)
                .atStartOfDay(businessClock.getZone()).toInstant();

        Map<DimensionKey, MutableTotals> totals = new LinkedHashMap<>();
        snapshotRepository.findNxtOpening(fromDate.minusDays(1), warehouseId,
                materialGroupId, materialId, conditions).forEach(snapshot -> totals.put(
                        key(snapshot.getWarehouse(), snapshot.getMaterial(),
                                snapshot.getCondition()),
                        MutableTotals.fromOpening(snapshot)));

        transactionRepository.aggregateNxtImports(warehouseId, materialGroupId, materialId,
                conditions, fromInclusive, toExclusive).forEach(row -> addImport(totals,
                        row.getWarehouse(), row.getMaterial(), row.getCondition(),
                        row.getQuantity()));
        transferRepository.aggregateNxtDestinationImports(warehouseId, materialGroupId,
                materialId, conditions, fromInclusive, toExclusive).forEach(row ->
                        addImport(totals, row.getWarehouse(), row.getMaterial(),
                                row.getCondition(), row.getQuantity()));
        transactionRepository.aggregateNxtExports(warehouseId, materialGroupId, materialId,
                conditions, fromInclusive, toExclusive).forEach(row -> addExport(totals,
                        row.getWarehouse(), row.getMaterial(), row.getCondition(),
                        row.getQuantity()));
        transferRepository.aggregateNxtSourceExports(warehouseId, materialGroupId, materialId,
                conditions, fromInclusive, toExclusive).forEach(row -> addExport(totals,
                        row.getWarehouse(), row.getMaterial(), row.getCondition(),
                        row.getQuantity()));

        if (totals.values().stream().anyMatch(total -> total.opening() == null)) {
            throw new MissingOpeningException(fromDate.minusDays(1));
        }

        List<NxtRow> rows = totals.values().stream()
                .map(MutableTotals::toRow)
                .sorted((left, right) -> {
                    int material = left.materialCode().compareTo(right.materialCode());
                    if (material != 0) return material;
                    int condition = left.condition().compareTo(right.condition());
                    if (condition != 0) return condition;
                    return left.warehouseCode().compareTo(right.warehouseCode());
                })
                .toList();
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 20;
        int start = (int) Math.min((long) safePage * safeSize, rows.size());
        int end = Math.min(start + safeSize, rows.size());
        Page<NxtRow> resultPage = new PageImpl<>(rows.subList(start, end),
                PageRequest.of(safePage, safeSize), rows.size());
        return new NxtReport(resultPage, fromDate.minusDays(1));
    }

    @Transactional(readOnly = true)
    public List<Warehouse> warehouses() {
        return warehouseRepository.findAllByOrderByWarehouseCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<MaterialGroup> materialGroups() {
        return materialGroupRepository.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<Material> materials() {
        return materialRepository.findAllByOrderByMaterialCodeAsc();
    }

    private void validate(Long warehouseId, LocalDate fromDate, LocalDate toDate) {
        if (warehouseId == null) {
            throw new InvalidSearchException("Vui lòng chọn kho hàng.");
        }
        if (fromDate == null || toDate == null) {
            throw new InvalidSearchException("Vui lòng nhập đầy đủ Từ ngày và Đến ngày.");
        }
        if (toDate.isBefore(fromDate)) {
            throw new InvalidSearchException("Đến ngày phải bằng hoặc sau Từ ngày.");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) > 90) {
            throw new InvalidSearchException("Khoảng thời gian truy vấn không được vượt quá 90 ngày.");
        }
    }

    private List<MaterialCondition> normalizedConditions(List<MaterialCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of(MaterialCondition.values());
        }
        return new ArrayList<>(EnumSet.copyOf(conditions));
    }

    private void addImport(Map<DimensionKey, MutableTotals> totals, Warehouse warehouse,
            Material material, MaterialCondition condition, long quantity) {
        DimensionKey key = key(warehouse, material, condition);
        totals.compute(key, (ignored, current) -> current == null
                ? MutableTotals.withoutOpening(warehouse, material, condition)
                        .addImport(quantity)
                : current.addImport(quantity));
    }

    private void addExport(Map<DimensionKey, MutableTotals> totals, Warehouse warehouse,
            Material material, MaterialCondition condition, long quantity) {
        DimensionKey key = key(warehouse, material, condition);
        totals.compute(key, (ignored, current) -> current == null
                ? MutableTotals.withoutOpening(warehouse, material, condition)
                        .addExport(quantity)
                : current.addExport(quantity));
    }

    private DimensionKey key(Warehouse warehouse, Material material,
            MaterialCondition condition) {
        return new DimensionKey(warehouse.getId(), material.getId(), condition);
    }

    public record NxtReport(Page<NxtRow> rows, LocalDate openingDate) {}

    public record NxtRow(String warehouseCode, String warehouseName,
                         String materialGroupCode, String materialGroupName,
                         String materialCode, String materialName, MaterialUnit unit,
                         MaterialCondition condition, long openingQuantity,
                         long importQuantity, long exportQuantity, long closingQuantity) {}

    public static final class InvalidSearchException extends RuntimeException {
        public InvalidSearchException(String message) {
            super(message);
        }
    }

    public static final class MissingOpeningException extends RuntimeException {
        private final LocalDate openingDate;

        public MissingOpeningException(LocalDate openingDate) {
            super("Thiếu dữ liệu tồn đầu kỳ ngày " + openingDate + ".");
            this.openingDate = openingDate;
        }

        public LocalDate getOpeningDate() {
            return openingDate;
        }
    }

    private record DimensionKey(Long warehouseId, Long materialId,
                                MaterialCondition condition) {}

    private record MutableTotals(Warehouse warehouse, Material material,
                                 MaterialCondition condition, Long opening,
                                 long imported, long exported) {
        static MutableTotals fromOpening(InventoryDailySnapshot snapshot) {
            return new MutableTotals(snapshot.getWarehouse(), snapshot.getMaterial(),
                    snapshot.getCondition(), snapshot.getOnHandQuantity(), 0, 0);
        }

        static MutableTotals withoutOpening(Warehouse warehouse, Material material,
                MaterialCondition condition) {
            return new MutableTotals(warehouse, material, condition, null, 0, 0);
        }

        MutableTotals addImport(long quantity) {
            return new MutableTotals(warehouse, material, condition, opening,
                    imported + quantity, exported);
        }

        MutableTotals addExport(long quantity) {
            return new MutableTotals(warehouse, material, condition, opening,
                    imported, exported + quantity);
        }

        NxtRow toRow() {
            long openingQuantity = opening;
            MaterialGroup group = material.getMaterialGroup();
            return new NxtRow(warehouse.getWarehouseCode(), warehouse.getWarehouseName(),
                    group.getCode(), group.getName(), material.getMaterialCode(),
                    material.getMaterialName(), material.getUnit(), condition,
                    openingQuantity, imported, exported,
                    openingQuantity + imported - exported);
        }
    }
}