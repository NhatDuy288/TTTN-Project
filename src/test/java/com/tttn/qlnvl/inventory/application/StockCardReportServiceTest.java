package com.tttn.qlnvl.inventory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.inventory.domain.InventoryDailySnapshot;
import com.tttn.qlnvl.inventory.repository.InventoryDailySnapshotRepository;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.inventory.repository.StockCardMovementProjection;
import com.tttn.qlnvl.material.domain.Material;
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
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StockCardReportServiceTest {
    private final InventoryDailySnapshotRepository snapshots = mock(InventoryDailySnapshotRepository.class);
    private final InventoryLotRepository lots = mock(InventoryLotRepository.class);
    private final WarehouseTransactionRepository transactions = mock(WarehouseTransactionRepository.class);
    private final WarehouseTransferRepository transfers = mock(WarehouseTransferRepository.class);
    private final StockCardReportService service = new StockCardReportService(
            snapshots, lots, transactions, transfers, mock(WarehouseRepository.class),
            mock(MaterialGroupRepository.class), mock(MaterialRepository.class),
            Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"),
                    ZoneId.of("Asia/Ho_Chi_Minh")));
    private final LocalDate from = LocalDate.of(2026, 9, 1);
    private final LocalDate to = LocalDate.of(2026, 9, 8);

    @BeforeEach
    void opening() {
        InventoryDailySnapshot snapshot = mock(InventoryDailySnapshot.class);
        Warehouse warehouse = mock(Warehouse.class);
        Material material = mock(Material.class);
        when(warehouse.getId()).thenReturn(11L);
        when(material.getId()).thenReturn(21L);
        when(material.getMaterialCode()).thenReturn("MAT-1");
        when(material.getMaterialName()).thenReturn("Vật tư 1");
        when(snapshot.getWarehouse()).thenReturn(warehouse);
        when(snapshot.getMaterial()).thenReturn(material);
        when(snapshot.getCondition()).thenReturn(MaterialCondition.NEW);
        when(snapshot.getOnHandQuantity()).thenReturn(10L);
        when(snapshots.findNxtOpening(eq(from.minusDays(1)), eq(11L),
                eq(12L), any(), any())).thenReturn(List.of(snapshot));
    }

    @Test
    void keepsCostLayersAndComputesRunningBalanceAcrossAllPhysicalMovements() {
        StockCardMovementProjection receipt = row("2026-09-01T01:00:00Z", "GRN-1", 5,
                1, "10.00", "PO-1");
        StockCardMovementProjection issueLayer1 = row("2026-09-02T01:00:00Z", "GDN-1", 2,
                2, "10.00", "PO-1");
        StockCardMovementProjection issueLayer2 = row("2026-09-02T01:00:00Z", "GDN-1", 3,
                3, "20.00", "PO-2");
        StockCardMovementProjection transferIssue = row("2026-09-03T01:00:00Z", "TRF-1", 1,
                4, "10.00", "PO-1");
        StockCardMovementProjection transferReceipt = row("2026-09-04T01:00:00Z", "TRF-2", 3,
                5, "20.00", "PO-2");
        when(lots.findStockCardReceipts(eq(11L), eq(12L), any(), any(), any(), any()))
                .thenReturn(List.of(receipt));
        when(transactions.findStockCardIssues(eq(11L), eq(12L), any(), any(), any(), any()))
                .thenReturn(List.of(issueLayer2, issueLayer1));
        when(transfers.findStockCardTransferIssues(eq(11L), eq(12L), any(), any(), any(), any()))
                .thenReturn(List.of(transferIssue));
        when(lots.findStockCardTransferReceipts(eq(11L), eq(12L), any(), any(), any(), any()))
                .thenReturn(List.of(transferReceipt));

        var report = service.report(11L, 12L, null, null, from, to, 0, 20);
        var rows = report.movements().getContent();
        assertEquals(10, report.openings().getFirst().openingQuantity());
        assertEquals(List.of(15L, 13L, 10L, 9L, 12L),
                rows.stream().map(StockCardReportService.StockCardRow::runningBalance).toList());
        assertEquals("GDN-1", rows.get(1).documentCode());
        assertEquals("GDN-1", rows.get(2).documentCode());
        assertEquals(new BigDecimal("10.00"), rows.get(1).unitPrice());
        assertEquals(new BigDecimal("20.00"), rows.get(2).unitPrice());
        assertEquals("PO-2", rows.get(2).purchaseOrderCode());
        assertEquals(3, rows.get(4).importQuantity());
        assertEquals(0, rows.get(4).exportQuantity());
    }

    @Test
    void missingOpeningStopsWholeReport() {
        StockCardMovementProjection unknown = row("2026-09-01T01:00:00Z", "GDN-1", 2,
                1, "10.00", null);
        when(unknown.getMaterialId()).thenReturn(99L);
        when(transactions.findStockCardIssues(eq(11L), eq(12L), any(), any(), any(), any()))
                .thenReturn(List.of(unknown));
        assertThrows(StockCardReportService.MissingOpeningException.class,
                () -> service.report(11L, 12L, null, null, from, to, 0, 20));
    }

    @Test
    void noOpeningSnapshotDoesNotBecomeAnEmptyOrZeroReport() {
        when(snapshots.findNxtOpening(eq(from.minusDays(1)), eq(11L),
                eq(12L), any(), any())).thenReturn(List.of());
        assertThrows(StockCardReportService.MissingOpeningException.class,
                () -> service.report(11L, 12L, null, null, from, to, 0, 20));
    }

    @Test
    void requiresGroupAndValidDateRange() {
        assertThrows(StockCardReportService.InvalidSearchException.class,
                () -> service.report(11L, null, null, null, from, to, 0, 20));
        assertThrows(StockCardReportService.InvalidSearchException.class,
                () -> service.report(11L, 12L, null, null, from, from.plusDays(91), 0, 20));
    }

    private StockCardMovementProjection row(String date, String code, long quantity,
            long layerId, String price, String poCode) {
        StockCardMovementProjection row = mock(StockCardMovementProjection.class);
        when(row.getTransactionDate()).thenReturn(Instant.parse(date));
        when(row.getDocumentCode()).thenReturn(code);
        when(row.getOperationTypeName()).thenReturn("Nghiệp vụ");
        when(row.getWarehouseId()).thenReturn(11L);
        when(row.getWarehouseCode()).thenReturn("WH-1");
        when(row.getWarehouseName()).thenReturn("Kho 1");
        when(row.getMaterialId()).thenReturn(21L);
        when(row.getMaterialCode()).thenReturn("MAT-1");
        when(row.getMaterialName()).thenReturn("Vật tư 1");
        when(row.getCondition()).thenReturn(MaterialCondition.NEW);
        when(row.getReasonName()).thenReturn("Lý do");
        when(row.getUnitPrice()).thenReturn(new BigDecimal(price));
        when(row.getQuantity()).thenReturn(quantity);
        when(row.getPurchaseOrderCode()).thenReturn(poCode);
        when(row.getLayerId()).thenReturn(layerId);
        return row;
    }
}
