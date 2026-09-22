package com.tttn.qlnvl.inventory.application;

import com.tttn.qlnvl.inventory.domain.InventoryDailySnapshot;
import com.tttn.qlnvl.inventory.repository.InventoryDailySnapshotRepository;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.inventory.repository.StockCardMovementProjection;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
public class StockCardReportService {
    private static final List<Integer> PAGE_SIZES = List.of(20, 50, 100);

    private final InventoryDailySnapshotRepository snapshots;
    private final InventoryLotRepository lots;
    private final WarehouseTransactionRepository transactions;
    private final WarehouseTransferRepository transfers;
    private final WarehouseRepository warehouses;
    private final MaterialGroupRepository groups;
    private final MaterialRepository materials;
    private final Clock businessClock;

    public StockCardReportService(InventoryDailySnapshotRepository snapshots,
            InventoryLotRepository lots, WarehouseTransactionRepository transactions,
            WarehouseTransferRepository transfers, WarehouseRepository warehouses,
            MaterialGroupRepository groups, MaterialRepository materials,
            Clock businessClock) {
        this.snapshots = snapshots;
        this.lots = lots;
        this.transactions = transactions;
        this.transfers = transfers;
        this.warehouses = warehouses;
        this.groups = groups;
        this.materials = materials;
        this.businessClock = businessClock;
    }

    public LocalDate defaultToDate() {
        return LocalDate.now(businessClock);
    }

    public LocalDate defaultFromDate() {
        return defaultToDate().minusDays(7);
    }

    @Transactional(readOnly = true)
    public StockCardReport report(Long warehouseId, Long materialGroupId, Long materialId,
            List<MaterialCondition> selectedConditions, LocalDate fromDate,
            LocalDate toDate, int page, int size) {
        validate(warehouseId, materialGroupId, fromDate, toDate);
        List<MaterialCondition> conditions = normalizedConditions(selectedConditions);
        LocalDate openingDate = fromDate.minusDays(1);
        Instant fromInclusive = fromDate.atStartOfDay(businessClock.getZone()).toInstant();
        Instant toExclusive = toDate.plusDays(1)
                .atStartOfDay(businessClock.getZone()).toInstant();

        Map<DimensionKey, Long> openingBalances = new LinkedHashMap<>();
        List<OpeningRow> openingRows = new ArrayList<>();
        for (InventoryDailySnapshot snapshot : snapshots.findNxtOpening(openingDate,
                warehouseId, materialGroupId, materialId, conditions)) {
            Material material = snapshot.getMaterial();
            openingBalances.put(new DimensionKey(snapshot.getWarehouse().getId(),
                    material.getId(), snapshot.getCondition()), snapshot.getOnHandQuantity());
            openingRows.add(new OpeningRow(material.getMaterialCode(),
                    material.getMaterialName(), snapshot.getCondition(),
                    snapshot.getOnHandQuantity()));
        }
        openingRows.sort(Comparator.comparing(OpeningRow::materialCode)
                .thenComparing(OpeningRow::condition));
        if (openingRows.isEmpty()) {
            throw new MissingOpeningException(openingDate);
        }

        List<Movement> movements = new ArrayList<>();
        addMovements(movements, lots.findStockCardReceipts(warehouseId, materialGroupId,
                materialId, conditions, fromInclusive, toExclusive), true);
        addMovements(movements, transactions.findStockCardIssues(warehouseId,
                materialGroupId, materialId, conditions, fromInclusive, toExclusive), false);
        addMovements(movements, transfers.findStockCardTransferIssues(warehouseId,
                materialGroupId, materialId, conditions, fromInclusive, toExclusive), false);
        addMovements(movements, lots.findStockCardTransferReceipts(warehouseId,
                materialGroupId, materialId, conditions, fromInclusive, toExclusive), true);

        if (movements.stream().anyMatch(movement ->
                !openingBalances.containsKey(movement.key()))) {
            throw new MissingOpeningException(openingDate);
        }
        movements.sort(Comparator.comparing((Movement movement) ->
                        movement.source().getMaterialCode())
                .thenComparing(movement -> movement.source().getCondition())
                .thenComparing(movement -> movement.source().getTransactionDate())
                .thenComparing(movement -> movement.source().getDocumentCode())
                .thenComparing(movement -> movement.source().getLayerId()));

        Map<DimensionKey, Long> running = new LinkedHashMap<>(openingBalances);
        List<StockCardRow> rows = new ArrayList<>(movements.size());
        for (Movement movement : movements) {
            StockCardMovementProjection source = movement.source();
            long balance = running.get(movement.key())
                    + (movement.importMovement() ? source.getQuantity() : -source.getQuantity());
            running.put(movement.key(), balance);
            rows.add(new StockCardRow(source.getTransactionDate(),
                    source.getDocumentCode(), source.getOperationTypeName(),
                    source.getWarehouseCode(), source.getWarehouseName(),
                    source.getMaterialCode(), source.getMaterialName(),
                    source.getCondition(), source.getReasonName(),
                    source.getUnitPrice(), movement.importMovement() ? source.getQuantity() : 0,
                    movement.importMovement() ? 0 : source.getQuantity(), balance,
                    source.getPurchaseOrderCode()));
        }

        int safePage = Math.max(page, 0);
        int safeSize = PAGE_SIZES.contains(size) ? size : 20;
        int start = (int) Math.min((long) safePage * safeSize, rows.size());
        int end = Math.min(start + safeSize, rows.size());
        Page<StockCardRow> resultPage = new PageImpl<>(rows.subList(start, end),
                PageRequest.of(safePage, safeSize), rows.size());
        return new StockCardReport(List.copyOf(openingRows), resultPage, openingDate);
    }

    @Transactional(readOnly = true)
    public List<Warehouse> warehouses() {
        return warehouses.findAllByOrderByWarehouseCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<MaterialGroup> materialGroups() {
        return groups.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<Material> materials() {
        return materials.findAllByOrderByMaterialCodeAsc();
    }

    private void validate(Long warehouseId, Long materialGroupId,
            LocalDate fromDate, LocalDate toDate) {
        if (warehouseId == null || materialGroupId == null) {
            throw new InvalidSearchException("Vui lòng chọn kho và nhóm vật tư.");
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

    private List<MaterialCondition> normalizedConditions(
            List<MaterialCondition> selectedConditions) {
        if (selectedConditions == null || selectedConditions.isEmpty()) {
            return List.of(MaterialCondition.values());
        }
        return new ArrayList<>(EnumSet.copyOf(selectedConditions));
    }

    private void addMovements(List<Movement> movements,
            List<StockCardMovementProjection> sources, boolean importMovement) {
        for (StockCardMovementProjection source : sources) {
            movements.add(new Movement(source, importMovement,
                    new DimensionKey(source.getWarehouseId(), source.getMaterialId(),
                            source.getCondition())));
        }
    }

    public record StockCardReport(List<OpeningRow> openings,
                                  Page<StockCardRow> movements,
                                  LocalDate openingDate) {}

    public record OpeningRow(String materialCode, String materialName,
                             MaterialCondition condition, long openingQuantity) {}

    public record StockCardRow(Instant transactionDate, String documentCode,
                               String operationTypeName, String warehouseCode,
                               String warehouseName, String materialCode,
                               String materialName, MaterialCondition condition,
                               String reasonName, BigDecimal unitPrice,
                               long importQuantity, long exportQuantity,
                               long runningBalance, String purchaseOrderCode) {}

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

    private record Movement(StockCardMovementProjection source, boolean importMovement,
                            DimensionKey key) {}
}
