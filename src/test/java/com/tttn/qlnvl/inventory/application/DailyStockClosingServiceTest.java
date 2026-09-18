package com.tttn.qlnvl.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.inventory.domain.InventoryDailySnapshot;
import com.tttn.qlnvl.inventory.repository.InventoryDailySnapshotRepository;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.repository.StockReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DailyStockClosingServiceTest {
    private InventoryLotRepository lotRepository;
    private StockReservationRepository reservationRepository;
    private InventoryDailySnapshotRepository snapshotRepository;
    private DailyStockClosingService service;

    @BeforeEach
    void setUp() {
        lotRepository = mock(InventoryLotRepository.class);
        reservationRepository = mock(StockReservationRepository.class);
        snapshotRepository = mock(InventoryDailySnapshotRepository.class);
        service = new DailyStockClosingService(lotRepository, reservationRepository,
                snapshotRepository);
    }

    @Test
    void closeAggregatesLotAndActiveSoftReservationIntoSnapshot() {
        Warehouse warehouse = warehouse(11L);
        Material material = material(21L);
        InventoryLotRepository.DailyLotAggregate lot = lotAggregate(warehouse, material,
                MaterialCondition.NEW, 20L, 3L);
        StockReservationRepository.DailySoftReservationAggregate soft = softAggregate(
                warehouse, material, MaterialCondition.NEW, 4L);
        when(lotRepository.aggregateForDailySnapshot()).thenReturn(List.of(lot));
        when(reservationRepository.aggregateActiveSoftForDailySnapshot())
                .thenReturn(List.of(soft));
        when(snapshotRepository.findAllBySnapshotDate(LocalDate.of(2026, 9, 17)))
                .thenReturn(List.of());

        int count = service.close(LocalDate.of(2026, 9, 17));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryDailySnapshot>> snapshots = ArgumentCaptor.forClass(List.class);
        verify(snapshotRepository).lockForClosing();
        verify(snapshotRepository).saveAllAndFlush(snapshots.capture());
        assertThat(count).isEqualTo(1);
        assertThat(snapshots.getValue()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getSnapshotDate()).isEqualTo(LocalDate.of(2026, 9, 17));
            assertThat(snapshot.getWarehouse()).isSameAs(warehouse);
            assertThat(snapshot.getMaterial()).isSameAs(material);
            assertThat(snapshot.getCondition()).isEqualTo(MaterialCondition.NEW);
            assertThat(snapshot.getOnHandQuantity()).isEqualTo(20L);
            assertThat(snapshot.getReservedQuantity()).isEqualTo(7L);
            assertThat(snapshot.getAvailableQuantity()).isEqualTo(13L);
        });
    }

    @Test
    void closeRerunUpdatesExistingSnapshotInsteadOfCreatingDuplicate() {
        Warehouse warehouse = warehouse(11L);
        Material material = material(21L);
        InventoryDailySnapshot existing = mock(InventoryDailySnapshot.class);
        when(existing.getWarehouse()).thenReturn(warehouse);
        when(existing.getMaterial()).thenReturn(material);
        when(existing.getCondition()).thenReturn(MaterialCondition.OLD);
        InventoryLotRepository.DailyLotAggregate lot = lotAggregate(warehouse, material,
                MaterialCondition.OLD, 9L, 2L);
        StockReservationRepository.DailySoftReservationAggregate soft = softAggregate(
                warehouse, material, MaterialCondition.OLD, 1L);
        when(lotRepository.aggregateForDailySnapshot()).thenReturn(List.of(lot));
        when(reservationRepository.aggregateActiveSoftForDailySnapshot())
                .thenReturn(List.of(soft));
        LocalDate date = LocalDate.of(2026, 9, 17);
        when(snapshotRepository.findAllBySnapshotDate(date)).thenReturn(List.of(existing));

        int count = service.close(date);

        verify(existing).updateQuantities(9L, 3L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryDailySnapshot>> snapshots = ArgumentCaptor.forClass(List.class);
        verify(snapshotRepository).saveAllAndFlush(snapshots.capture());
        assertThat(count).isEqualTo(1);
        assertThat(snapshots.getValue()).containsExactly(existing);
    }

    @Test
    void scheduledJobUsesConfiguredBusinessTimezoneDate() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-17T18:00:00Z"),
                ZoneId.of("Asia/Ho_Chi_Minh"));
        DailyStockClosingService closingService = mock(DailyStockClosingService.class);
        DailyStockClosingJob job = new DailyStockClosingJob(closingService, clock);

        job.closeDailyInventory();

        verify(closingService).close(LocalDate.of(2026, 9, 18));
    }

    private Warehouse warehouse(Long id) {
        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getId()).thenReturn(id);
        return warehouse;
    }

    private Material material(Long id) {
        Material material = mock(Material.class);
        when(material.getId()).thenReturn(id);
        return material;
    }

    private InventoryLotRepository.DailyLotAggregate lotAggregate(Warehouse warehouse,
            Material material, MaterialCondition condition, long onHand, long reserved) {
        InventoryLotRepository.DailyLotAggregate row =
                mock(InventoryLotRepository.DailyLotAggregate.class);
        when(row.getWarehouse()).thenReturn(warehouse);
        when(row.getMaterial()).thenReturn(material);
        when(row.getCondition()).thenReturn(condition);
        when(row.getOnHandQuantity()).thenReturn(onHand);
        when(row.getLotReservedQuantity()).thenReturn(reserved);
        return row;
    }

    private StockReservationRepository.DailySoftReservationAggregate softAggregate(
            Warehouse warehouse, Material material, MaterialCondition condition, long quantity) {
        StockReservationRepository.DailySoftReservationAggregate row =
                mock(StockReservationRepository.DailySoftReservationAggregate.class);
        when(row.getWarehouse()).thenReturn(warehouse);
        when(row.getMaterial()).thenReturn(material);
        when(row.getCondition()).thenReturn(condition);
        when(row.getSoftReservedQuantity()).thenReturn(quantity);
        return row;
    }
}
