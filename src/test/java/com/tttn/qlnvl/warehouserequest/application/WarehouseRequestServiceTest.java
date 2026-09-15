package com.tttn.qlnvl.warehouserequest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.domain.MaterialStatus;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderRepository;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.domain.WarehouseStatus;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import com.tttn.qlnvl.warehouserequest.domain.Reason;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.OperationTypeRepository;
import com.tttn.qlnvl.warehouserequest.repository.ReasonRepository;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WarehouseRequestServiceTest {
    private WarehouseRequestRepository requestRepository;
    private OperationTypeRepository operationTypeRepository;
    private ReasonRepository reasonRepository;
    private WarehouseRepository warehouseRepository;
    private MaterialRepository materialRepository;
    private PurchaseOrderRepository purchaseOrderRepository;
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
        appUserRepository = mock(AppUserRepository.class);
        service = new WarehouseRequestService(requestRepository, operationTypeRepository,
                reasonRepository, warehouseRepository, materialRepository,
                purchaseOrderRepository, appUserRepository);
        when(requestRepository.saveAndFlush(any(WarehouseRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
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
