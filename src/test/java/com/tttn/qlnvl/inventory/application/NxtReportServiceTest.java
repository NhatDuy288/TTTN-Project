package com.tttn.qlnvl.inventory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.inventory.domain.InventoryDailySnapshot;
import com.tttn.qlnvl.inventory.repository.InventoryDailySnapshotRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository.NxtTransactionAggregate;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository.NxtTransferAggregate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NxtReportServiceTest {
    private final InventoryDailySnapshotRepository snapshots = mock(InventoryDailySnapshotRepository.class);
    private final WarehouseTransactionRepository transactions = mock(WarehouseTransactionRepository.class);
    private final WarehouseTransferRepository transfers = mock(WarehouseTransferRepository.class);
    private final WarehouseRepository warehouses = mock(WarehouseRepository.class);
    private final MaterialGroupRepository groups = mock(MaterialGroupRepository.class);
    private final MaterialRepository materials = mock(MaterialRepository.class);
    private final NxtReportService service = new NxtReportService(snapshots, transactions,
            transfers, warehouses, groups, materials, Clock.fixed(
                    Instant.parse("2026-09-08T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
    private final LocalDate from = LocalDate.of(2026, 9, 1);
    private final LocalDate to = LocalDate.of(2026, 9, 8);
    private Warehouse warehouse;
    private Material material;

    @BeforeEach
    void setUp() {
        warehouse = mock(Warehouse.class);
        material = mock(Material.class);
        MaterialGroup group = mock(MaterialGroup.class);
        when(warehouse.getId()).thenReturn(11L);
        when(warehouse.getWarehouseCode()).thenReturn("WH-1");
        when(warehouse.getWarehouseName()).thenReturn("Kho 1");
        when(material.getId()).thenReturn(21L);
        when(material.getMaterialCode()).thenReturn("MAT-1");
        when(material.getMaterialName()).thenReturn("Vật tư 1");
        when(material.getMaterialGroup()).thenReturn(group);
        when(group.getCode()).thenReturn("GR-1");
        when(group.getName()).thenReturn("Nhóm 1");
    }

    @Test
    void combinesConfirmedTransactionsAndBothTransferSides() {
        InventoryDailySnapshot opening = mock(InventoryDailySnapshot.class);
        when(opening.getWarehouse()).thenReturn(warehouse);
        when(opening.getMaterial()).thenReturn(material);
        when(opening.getCondition()).thenReturn(MaterialCondition.NEW);
        when(opening.getOnHandQuantity()).thenReturn(10L);
        when(snapshots.findNxtOpening(eq(from.minusDays(1)), eq(11L),
                any(), any(), any())).thenReturn(List.of(opening));
        NxtTransactionAggregate receipt = transactionRow(5);
        NxtTransferAggregate destination = transferRow(3);
        NxtTransactionAggregate issue = transactionRow(4);
        NxtTransferAggregate source = transferRow(2);
        when(transactions.aggregateNxtImports(eq(11L), any(), any(), any(), any(), any()))
                .thenReturn(List.of(receipt));
        when(transfers.aggregateNxtDestinationImports(eq(11L), any(), any(), any(), any(), any()))
                .thenReturn(List.of(destination));
        when(transactions.aggregateNxtExports(eq(11L), any(), any(), any(), any(), any()))
                .thenReturn(List.of(issue));
        when(transfers.aggregateNxtSourceExports(eq(11L), any(), any(), any(), any(), any()))
                .thenReturn(List.of(source));

        var report = service.report(11L, null, null, null, from, to, 0, 20);
        var row = report.rows().getContent().getFirst();
        assertEquals(10, row.openingQuantity());
        assertEquals(8, row.importQuantity());
        assertEquals(6, row.exportQuantity());
        assertEquals(12, row.closingQuantity());
        assertEquals(from.minusDays(1), report.openingDate());
    }

    @Test
    void movementWithoutOpeningStopsWholeScope() {
        NxtTransactionAggregate receipt = transactionRow(5);
        when(transactions.aggregateNxtImports(eq(11L), any(), any(), any(), any(), any()))
                .thenReturn(List.of(receipt));
        assertThrows(NxtReportService.MissingOpeningException.class,
                () -> service.report(11L, null, null, null, from, to, 0, 20));
    }

    @Test
    void rejectsDateRangeBeyondNinetyDays() {
        assertThrows(NxtReportService.InvalidSearchException.class,
                () -> service.report(11L, null, null, null, from, from.plusDays(91), 0, 20));
    }

    private NxtTransactionAggregate transactionRow(long quantity) {
        NxtTransactionAggregate row = mock(NxtTransactionAggregate.class);
        when(row.getWarehouse()).thenReturn(warehouse);
        when(row.getMaterial()).thenReturn(material);
        when(row.getCondition()).thenReturn(MaterialCondition.NEW);
        when(row.getQuantity()).thenReturn(quantity);
        return row;
    }

    private NxtTransferAggregate transferRow(long quantity) {
        NxtTransferAggregate row = mock(NxtTransferAggregate.class);
        when(row.getWarehouse()).thenReturn(warehouse);
        when(row.getMaterial()).thenReturn(material);
        when(row.getCondition()).thenReturn(MaterialCondition.NEW);
        when(row.getQuantity()).thenReturn(quantity);
        return row;
    }
}
