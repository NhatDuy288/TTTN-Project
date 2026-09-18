package com.tttn.qlnvl.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialUnit;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.repository.StockReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class InventoryReportServiceTest {
    private InventoryLotRepository lotRepository;
    private StockReservationRepository reservationRepository;
    private InventoryReportService service;

    @BeforeEach
    void setUp() {
        lotRepository = mock(InventoryLotRepository.class);
        reservationRepository = mock(StockReservationRepository.class);
        service = new InventoryReportService(lotRepository, reservationRepository,
                mock(WarehouseRepository.class), mock(MaterialGroupRepository.class),
                mock(MaterialRepository.class));
    }

    @Test
    void detailedInventoryKeepsLotAvailabilitySeparateFromActualAvailability() {
        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getId()).thenReturn(11L);
        when(warehouse.getWarehouseCode()).thenReturn("KHO-01");
        when(warehouse.getWarehouseName()).thenReturn("Kho chính");
        Material material = mock(Material.class);
        when(material.getId()).thenReturn(21L);
        when(material.getMaterialCode()).thenReturn("MAT-001");
        when(material.getMaterialName()).thenReturn("Giấy in");
        when(material.getUnit()).thenReturn(MaterialUnit.TO);

        PurchaseOrder purchaseOrder = mock(PurchaseOrder.class);
        when(purchaseOrder.getPoCode()).thenReturn("PO-001");
        PurchaseOrderItem purchaseOrderItem = mock(PurchaseOrderItem.class);
        when(purchaseOrderItem.getPurchaseOrder()).thenReturn(purchaseOrder);
        InventoryLot lot = mock(InventoryLot.class);
        when(lot.getId()).thenReturn(31L);
        when(lot.getWarehouse()).thenReturn(warehouse);
        when(lot.getMaterial()).thenReturn(material);
        when(lot.getCondition()).thenReturn(MaterialCondition.NEW);
        when(lot.getOnHandQuantity()).thenReturn(20L);
        when(lot.getReservedQuantity()).thenReturn(3L);
        when(lot.getUnitPrice()).thenReturn(new BigDecimal("12500.0000"));
        when(lot.getReceivedAt()).thenReturn(Instant.parse("2026-09-17T02:00:00Z"));
        when(lot.getSourcePurchaseOrderItem()).thenReturn(purchaseOrderItem);

        InventoryLotRepository.DetailedInventoryAggregate lotTotal =
                mock(InventoryLotRepository.DetailedInventoryAggregate.class);
        when(lotTotal.getWarehouse()).thenReturn(warehouse);
        when(lotTotal.getMaterial()).thenReturn(material);
        when(lotTotal.getCondition()).thenReturn(MaterialCondition.NEW);
        when(lotTotal.getOnHandQuantity()).thenReturn(20L);
        when(lotTotal.getLotReservedQuantity()).thenReturn(3L);
        StockReservationRepository.DetailedInventorySoftAggregate softTotal =
                mock(StockReservationRepository.DetailedInventorySoftAggregate.class);
        when(softTotal.getWarehouse()).thenReturn(warehouse);
        when(softTotal.getMaterial()).thenReturn(material);
        when(softTotal.getCondition()).thenReturn(MaterialCondition.NEW);
        when(softTotal.getSoftReservedQuantity()).thenReturn(4L);

        when(lotRepository.searchDetailedInventory(eq(11L), eq(12L), eq(21L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(lot)));
        when(lotRepository.aggregateDetailedInventory(eq(11L), eq(12L), eq(21L), any()))
                .thenReturn(List.of(lotTotal));
        when(reservationRepository.aggregateDetailedInventorySoftReservations(
                eq(11L), eq(12L), eq(21L), any())).thenReturn(List.of(softTotal));

        InventoryReportService.DetailedInventoryReport report = service.detailedInventory(
                11L, 12L, 21L, List.of(MaterialCondition.NEW), 0, 20);

        assertThat(report.lots().getContent()).singleElement().satisfies(row -> {
            assertThat(row.lotAvailable()).isEqualTo(17L);
            assertThat(row.poCode()).isEqualTo("PO-001");
        });
        assertThat(report.aggregates()).singleElement().satisfies(row -> {
            assertThat(row.onHand()).isEqualTo(20L);
            assertThat(row.lotReserved()).isEqualTo(3L);
            assertThat(row.softReserved()).isEqualTo(4L);
            assertThat(row.actualAvailable()).isEqualTo(13L);
        });
    }

    @Test
    void detailedInventoryNormalizesPaginationAndEmptyConditionToAllConditions() {
        when(lotRepository.searchDetailedInventory(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(lotRepository.aggregateDetailedInventory(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(reservationRepository.aggregateDetailedInventorySoftReservations(
                any(), any(), any(), any())).thenReturn(List.of());

        service.detailedInventory(null, null, null, List.of(), -4, 15);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaterialCondition>> conditions = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(lotRepository).searchDetailedInventory(eq(null), eq(null), eq(null),
                conditions.capture(), pageable.capture());
        assertThat(conditions.getValue()).containsExactlyInAnyOrder(MaterialCondition.values());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }
}