package com.tttn.qlnvl.warehouserequest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.domain.MaterialStatus;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderStatus;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderItemRepository;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderRepository;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.domain.WarehouseStatus;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import com.tttn.qlnvl.warehouserequest.domain.Reason;
import com.tttn.qlnvl.warehouserequest.domain.StockReservation;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestDetail;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.OperationTypeRepository;
import com.tttn.qlnvl.warehouserequest.repository.ReasonRepository;
import com.tttn.qlnvl.warehouserequest.repository.StockReservationRepository;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class WarehouseRequestServiceTest {
    private WarehouseRequestRepository requestRepository;
    private OperationTypeRepository operationTypeRepository;
    private ReasonRepository reasonRepository;
    private WarehouseRepository warehouseRepository;
    private MaterialRepository materialRepository;
    private PurchaseOrderRepository purchaseOrderRepository;
    private PurchaseOrderItemRepository purchaseOrderItemRepository;
    private InventoryLotRepository inventoryLotRepository;
    private StockReservationRepository stockReservationRepository;
    private StatusHistoryRepository statusHistoryRepository;
    private AppUserRepository appUserRepository;
    private WarehouseRequestService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(WarehouseRequestRepository.class);
        operationTypeRepository = mock(OperationTypeRepository.class);
        reasonRepository = mock(ReasonRepository.class);
        warehouseRepository = mock(WarehouseRepository.class);
        materialRepository = mock(MaterialRepository.class);
        purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        purchaseOrderItemRepository = mock(PurchaseOrderItemRepository.class);
        inventoryLotRepository = mock(InventoryLotRepository.class);
        stockReservationRepository = mock(StockReservationRepository.class);
        statusHistoryRepository = mock(StatusHistoryRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        service = new WarehouseRequestService(requestRepository, operationTypeRepository,
                reasonRepository, warehouseRepository, materialRepository,
                purchaseOrderRepository, purchaseOrderItemRepository, inventoryLotRepository,
                stockReservationRepository, statusHistoryRepository, appUserRepository);
        when(requestRepository.saveAndFlush(any(WarehouseRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void searchesOnlyOwnedRequestsWithSafePagingAndRecentActivitySort() {
        when(requestRepository.findByCreatedByIdAndRequestCodeContainingIgnoreCaseAndStatus(
                eq(99L), eq("OUT"),
                eq(WarehouseRequestStatus.SUBMITTED), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.searchOwned(99L, "  OUT  ", WarehouseRequestStatus.SUBMITTED, -2, 999);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(requestRepository).findByCreatedByIdAndRequestCodeContainingIgnoreCaseAndStatus(
                eq(99L), eq("OUT"),
                eq(WarehouseRequestStatus.SUBMITTED), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("updatedAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void searchesOwnedRequestsWithoutOptionalFilters() {
        when(requestRepository.findByCreatedById(eq(99L), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.searchOwned(99L, "   ", null, 0, 20);

        verify(requestRepository).findByCreatedById(eq(99L), any(Pageable.class));
    }

    @Test
    void createsEmptyImportDraftAndGeneratesDailyCode() {
        OperationType operation = operation(1L, OperationDirection.IMPORT, false,
                Set.of(), Set.of(MaterialCondition.NEW));
        Reason reason = reason(OperationDirection.IMPORT, false);
        Warehouse destination = warehouse(10L);
        AppUser creator = mock(AppUser.class);
        when(operationTypeRepository.findDetailedByIdAndActiveTrue(1L)).thenReturn(Optional.of(operation));
        when(reasonRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(reason));
        when(warehouseRepository.findById(10L)).thenReturn(Optional.of(destination));
        when(appUserRepository.findById(99L)).thenReturn(Optional.of(creator));
        when(requestRepository.maxSequenceFor(any())).thenReturn(0);

        WarehouseRequest result = service.createDraft(
                new WarehouseRequestDraftCommand(1L, 2L, null, 10L, null, "  ghi chú  ", List.of()), 99L);

        assertThat(result.getRequestCode()).matches("INP_\\d{8}_001");
        assertThat(result.getStatus()).isEqualTo(WarehouseRequestStatus.DRAFT);
        assertThat(result.getDestinationWarehouse()).isSameAs(destination);
        assertThat(result.getNote()).isEqualTo("ghi chú");
        assertThat(result.getDetails()).isEmpty();
        verify(requestRepository).lockForCodeGeneration();
        verify(materialRepository, never()).findDetailedByIdIn(any());
    }

    @Test
    void rejectsReasonFromAnotherDirection() {
        OperationType operation = operation(1L, OperationDirection.IMPORT, false, Set.of(), Set.of());
        Reason reason = reason(OperationDirection.EXPORT, false);
        when(operationTypeRepository.findDetailedByIdAndActiveTrue(1L)).thenReturn(Optional.of(operation));
        when(reasonRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(reason));

        assertInvalid("reasonId", () -> service.createDraft(
                new WarehouseRequestDraftCommand(1L, 2L, null, 10L, null, null, List.of()), 99L));
    }

    @Test
    void requiresNoteForOtherReason() {
        OperationType operation = operation(1L, OperationDirection.IMPORT, false, Set.of(), Set.of());
        Reason reason = reason(OperationDirection.IMPORT, true);
        when(operationTypeRepository.findDetailedByIdAndActiveTrue(1L)).thenReturn(Optional.of(operation));
        when(reasonRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(reason));

        assertInvalid("note", () -> service.createDraft(
                new WarehouseRequestDraftCommand(1L, 2L, null, 10L, null, "  ", List.of()), 99L));
    }

    @Test
    void rejectsSameSourceAndDestinationForTransfer() {
        OperationType operation = operation(1L, OperationDirection.TRANSFER, false, Set.of(), Set.of());
        Reason reason = reason(OperationDirection.TRANSFER, false);
        when(operationTypeRepository.findDetailedByIdAndActiveTrue(1L)).thenReturn(Optional.of(operation));
        when(reasonRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(reason));
        Warehouse warehouse = warehouse(10L);
        when(warehouseRepository.findById(10L)).thenReturn(Optional.of(warehouse));

        assertInvalid("destinationWarehouseId", () -> service.createDraft(
                new WarehouseRequestDraftCommand(1L, 2L, 10L, 10L, null, null, List.of()), 99L));
    }

    @Test
    void validatesMaterialGroupAndConditionThenCreatesDetail() {
        MaterialGroup group = mock(MaterialGroup.class);
        when(group.getId()).thenReturn(20L);
        OperationType operation = operation(1L, OperationDirection.EXPORT, false,
                Set.of(group), Set.of(MaterialCondition.NEW));
        Reason reason = reason(OperationDirection.EXPORT, false);
        Warehouse source = warehouse(10L);
        Material material = mock(Material.class);
        when(material.getId()).thenReturn(30L);
        when(material.getStatus()).thenReturn(MaterialStatus.ACTIVE);
        when(material.getMaterialGroup()).thenReturn(group);
        when(operationTypeRepository.findDetailedByIdAndActiveTrue(1L)).thenReturn(Optional.of(operation));
        when(reasonRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(reason));
        when(warehouseRepository.findById(10L)).thenReturn(Optional.of(source));
        when(materialRepository.findDetailedByIdIn(List.of(30L))).thenReturn(List.of(material));
        when(appUserRepository.findById(99L)).thenReturn(Optional.of(mock(AppUser.class)));

        WarehouseRequest result = service.createDraft(new WarehouseRequestDraftCommand(1L, 2L,
                10L, null, null, null, List.of(
                        new WarehouseRequestDraftCommand.Detail(30L, MaterialCondition.NEW, 5L, null))), 99L);

        assertThat(result.getDetails()).hasSize(1);
        assertThat(result.getDetails().get(0).getQuantity()).isEqualTo(5);
        assertThat(result.getRequestCode()).startsWith("OUT_");
    }

    @Test
    void rejectsPurchaseOrderItemThatDoesNotMatchMaterial() {
        MaterialGroup group = mock(MaterialGroup.class);
        when(group.getId()).thenReturn(20L);
        OperationType operation = operation(1L, OperationDirection.IMPORT, true,
                Set.of(group), Set.of(MaterialCondition.NEW));
        Material material = activeMaterial(30L, group);
        Material otherMaterial = activeMaterial(31L, group);
        PurchaseOrderItem item = mock(PurchaseOrderItem.class);
        when(item.getId()).thenReturn(40L);
        when(item.getMaterial()).thenReturn(otherMaterial);
        PurchaseOrder po = mock(PurchaseOrder.class);
        when(po.getItems()).thenReturn(List.of(item));
        Reason reason = reason(OperationDirection.IMPORT, false);
        Warehouse destination = warehouse(10L);
        when(operationTypeRepository.findDetailedByIdAndActiveTrue(1L)).thenReturn(Optional.of(operation));
        when(reasonRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(reason));
        when(warehouseRepository.findById(10L)).thenReturn(Optional.of(destination));
        when(purchaseOrderRepository.findDetailedById(50L)).thenReturn(Optional.of(po));
        when(materialRepository.findDetailedByIdIn(List.of(30L))).thenReturn(List.of(material));

        assertInvalid("details[0].purchaseOrderItemId", () -> service.createDraft(
                new WarehouseRequestDraftCommand(1L, 2L, null, 10L, 50L, null,
                        List.of(new WarehouseRequestDraftCommand.Detail(
                                30L, MaterialCondition.NEW, 1L, 40L))), 99L));
    }

    @Test
    void updateRejectsChangingOperationType() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        OperationType operation = operation(1L, OperationDirection.IMPORT, false, Set.of(), Set.of());
        AppUser owner = mock(AppUser.class);
        when(owner.getId()).thenReturn(99L);
        when(request.getCreatedBy()).thenReturn(owner);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.DRAFT);
        when(request.getOperationType()).thenReturn(operation);
        when(requestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));

        assertInvalid("operationTypeId", () -> service.updateDraft(5L,
                new WarehouseRequestDraftCommand(7L, 2L, null, 10L, null, null, List.of()), 99L));
        verify(request, never()).updateDraft(any(), any(), any(), any());
    }

    @Test
    void submitExportLocksAvailabilityCreatesSoftReservationAndHistory() {
        MaterialGroup group = mock(MaterialGroup.class);
        when(group.getId()).thenReturn(20L);
        OperationType operation = operation(1L, OperationDirection.EXPORT, false,
                Set.of(group), Set.of(MaterialCondition.NEW));
        Warehouse source = warehouse(10L);
        Material material = activeMaterial(30L, group);
        WarehouseRequestDetail detail = mock(WarehouseRequestDetail.class);
        when(detail.getMaterial()).thenReturn(material);
        when(detail.getCondition()).thenReturn(MaterialCondition.NEW);
        when(detail.getQuantity()).thenReturn(5L);
        WarehouseRequest request = submitRequest(7L, 99L, operation, source, null, null, List.of(detail));
        InventoryLot lot = mock(InventoryLot.class);
        when(lot.getOnHandQuantity()).thenReturn(10L);
        when(lot.getReservedQuantity()).thenReturn(1L);
        when(requestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));
        when(inventoryLotRepository.findDimensionForUpdate(10L, 30L, MaterialCondition.NEW))
                .thenReturn(List.of(lot));
        when(stockReservationRepository.activeSoftQuantity(10L, 30L, MaterialCondition.NEW))
                .thenReturn(2L);

        service.submit(7L, 99L);

        verify(request).submit();
        verify(stockReservationRepository).saveAll(any());
        verify(statusHistoryRepository).save(any(StatusHistory.class));
    }

    @Test
    void submitExportRejectsInsufficientAvailabilityWithoutMutation() {
        MaterialGroup group = mock(MaterialGroup.class);
        when(group.getId()).thenReturn(20L);
        OperationType operation = operation(1L, OperationDirection.EXPORT, false,
                Set.of(group), Set.of(MaterialCondition.NEW));
        Warehouse source = warehouse(10L);
        Material material = activeMaterial(30L, group);
        WarehouseRequestDetail detail = mock(WarehouseRequestDetail.class);
        when(detail.getMaterial()).thenReturn(material);
        when(detail.getCondition()).thenReturn(MaterialCondition.NEW);
        when(detail.getQuantity()).thenReturn(5L);
        WarehouseRequest request = submitRequest(7L, 99L, operation, source, null, null, List.of(detail));
        InventoryLot lot = mock(InventoryLot.class);
        when(lot.getOnHandQuantity()).thenReturn(5L);
        when(lot.getReservedQuantity()).thenReturn(1L);
        when(requestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));
        when(inventoryLotRepository.findDimensionForUpdate(10L, 30L, MaterialCondition.NEW))
                .thenReturn(List.of(lot));
        when(stockReservationRepository.activeSoftQuantity(10L, 30L, MaterialCondition.NEW))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.submit(7L, 99L))
                .isInstanceOf(InvalidWarehouseRequestException.class);

        verify(request, never()).submit();
        verify(stockReservationRepository, never()).saveAll(any());
        verify(statusHistoryRepository, never()).save(any());
    }

    @Test
    void submitPoImportLocksItemAndChecksDerivedCommitment() {
        MaterialGroup group = mock(MaterialGroup.class);
        when(group.getId()).thenReturn(20L);
        OperationType operation = operation(1L, OperationDirection.IMPORT, true,
                Set.of(group), Set.of(MaterialCondition.NEW));
        Warehouse destination = warehouse(11L);
        Material material = activeMaterial(30L, group);
        PurchaseOrder purchaseOrder = mock(PurchaseOrder.class);
        when(purchaseOrder.getId()).thenReturn(50L);
        when(purchaseOrder.getStatus()).thenReturn(PurchaseOrderStatus.OPEN);
        PurchaseOrderItem item = mock(PurchaseOrderItem.class);
        when(item.getId()).thenReturn(40L);
        when(item.getPurchaseOrder()).thenReturn(purchaseOrder);
        when(item.getOrderedQuantity()).thenReturn(10L);
        WarehouseRequestDetail detail = mock(WarehouseRequestDetail.class);
        when(detail.getMaterial()).thenReturn(material);
        when(detail.getCondition()).thenReturn(MaterialCondition.NEW);
        when(detail.getQuantity()).thenReturn(4L);
        when(detail.getPurchaseOrderItem()).thenReturn(item);
        WarehouseRequest request = submitRequest(7L, 99L, operation, null, destination,
                purchaseOrder, List.of(detail));
        when(requestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));
        when(purchaseOrderRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(purchaseOrder));
        when(purchaseOrderItemRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(item));
        when(purchaseOrderItemRepository.committedQuantity(40L)).thenReturn(6L);

        service.submit(7L, 99L);

        verify(request).submit();
        verify(stockReservationRepository, never()).saveAll(any());
        verify(statusHistoryRepository).save(any(StatusHistory.class));
    }

    @Test
    void cancelDraftChangesStatusAndWritesHistoryWithoutReservationRelease() {
        OperationType operation = operation(1L, OperationDirection.IMPORT, false,
                Set.of(), Set.of());
        WarehouseRequest request = submitRequest(7L, 99L, operation, null, warehouse(11L),
                null, List.of());
        when(requestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));

        service.cancel(7L, 99L);

        verify(request).cancel();
        verify(stockReservationRepository, never()).findActiveByRequestIdForUpdate(any());
        verify(statusHistoryRepository).save(any(StatusHistory.class));
    }

    @Test
    void cancelSubmittedExportReleasesActiveSoftReservations() {
        OperationType operation = operation(1L, OperationDirection.EXPORT, false,
                Set.of(), Set.of());
        WarehouseRequest request = submitRequest(7L, 99L, operation, warehouse(10L), null,
                null, List.of());
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.SUBMITTED);
        StockReservation reservation = mock(StockReservation.class);
        when(requestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));
        when(stockReservationRepository.findActiveByRequestIdForUpdate(7L))
                .thenReturn(List.of(reservation));

        service.cancel(7L, 99L);

        verify(reservation).release();
        verify(stockReservationRepository).saveAll(List.of(reservation));
        verify(request).cancel();
        verify(statusHistoryRepository).save(any(StatusHistory.class));
    }

    @Test
    void cancelRejectsRequestAfterApprovalWithoutMutation() {
        OperationType operation = operation(1L, OperationDirection.EXPORT, false,
                Set.of(), Set.of());
        WarehouseRequest request = submitRequest(7L, 99L, operation, warehouse(10L), null,
                null, List.of());
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.APPROVED);
        when(requestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(7L, 99L))
                .isInstanceOf(WarehouseRequestConflictException.class);

        verify(request, never()).cancel();
        verify(stockReservationRepository, never()).findActiveByRequestIdForUpdate(any());
        verify(statusHistoryRepository, never()).save(any());
    }

    private WarehouseRequest submitRequest(Long id, Long ownerId, OperationType operation,
            Warehouse source, Warehouse destination, PurchaseOrder purchaseOrder,
            List<WarehouseRequestDetail> details) {
        WarehouseRequest request = mock(WarehouseRequest.class);
        AppUser owner = mock(AppUser.class);
        Reason reason = reason(operation.getDirection(), false);
        when(owner.getId()).thenReturn(ownerId);
        when(request.getId()).thenReturn(id);
        when(request.getCreatedBy()).thenReturn(owner);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.DRAFT);
        when(request.getOperationType()).thenReturn(operation);
        when(request.getReason()).thenReturn(reason);
        when(request.getSourceWarehouse()).thenReturn(source);
        when(request.getDestinationWarehouse()).thenReturn(destination);
        when(request.getPurchaseOrder()).thenReturn(purchaseOrder);
        when(request.getDetails()).thenReturn(details);
        return request;
    }

    private OperationType operation(Long id, OperationDirection direction, boolean requiresPo,
            Set<MaterialGroup> groups, Set<MaterialCondition> conditions) {
        OperationType operation = mock(OperationType.class);
        when(operation.getId()).thenReturn(id);
        when(operation.getDirection()).thenReturn(direction);
        when(operation.isRequiresPo()).thenReturn(requiresPo);
        when(operation.isActive()).thenReturn(true);
        when(operation.getMaterialGroups()).thenReturn(groups);
        when(operation.getAllowedConditions()).thenReturn(conditions);
        return operation;
    }

    private Reason reason(OperationDirection direction, boolean requiresNote) {
        Reason reason = mock(Reason.class);
        when(reason.getDirection()).thenReturn(direction);
        when(reason.isRequiresNote()).thenReturn(requiresNote);
        when(reason.isActive()).thenReturn(true);
        return reason;
    }

    private Warehouse warehouse(Long id) {
        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getId()).thenReturn(id);
        when(warehouse.getStatus()).thenReturn(WarehouseStatus.ACTIVE);
        return warehouse;
    }

    private Material activeMaterial(Long id, MaterialGroup group) {
        Material material = mock(Material.class);
        when(material.getId()).thenReturn(id);
        when(material.getStatus()).thenReturn(MaterialStatus.ACTIVE);
        when(material.getMaterialGroup()).thenReturn(group);
        return material;
    }

    private void assertInvalid(String field, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(InvalidWarehouseRequestException.class,
                error -> assertThat(error.getField()).isEqualTo(field));
    }
}
