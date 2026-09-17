package com.tttn.qlnvl.warehousetransaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderStatus;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderItemRepository;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderRepository;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestDetail;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import com.tttn.qlnvl.warehousetransaction.domain.IssueLotAllocation;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionDetail;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class TransactionConfirmationServiceTest {
    private WarehouseTransactionRepository transactionRepository;
    private WarehouseRequestRepository requestRepository;
    private InventoryLotRepository lotRepository;
    private PurchaseOrderRepository purchaseOrderRepository;
    private PurchaseOrderItemRepository itemRepository;
    private StatusHistoryRepository historyRepository;
    private AppUserRepository userRepository;
    private TransactionConfirmationService service;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(WarehouseTransactionRepository.class);
        requestRepository = mock(WarehouseRequestRepository.class);
        lotRepository = mock(InventoryLotRepository.class);
        purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        itemRepository = mock(PurchaseOrderItemRepository.class);
        historyRepository = mock(StatusHistoryRepository.class);
        userRepository = mock(AppUserRepository.class);
        service = new TransactionConfirmationService(transactionRepository, requestRepository,
                lotRepository, purchaseOrderRepository, itemRepository, historyRepository,
                userRepository);
    }

    @Test
    void queueUsesReadyForConfirmationAndSafePagination() {
        when(transactionRepository.findQueue(any(), any())).thenReturn(Page.empty());

        service.queue(-1, 50);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findQueue(
                org.mockito.ArgumentMatchers.eq(WarehouseTransactionStatus.READY_FOR_CONFIRMATION),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void confirmImportCreatesOneReceiptLotAndCompletesWorkflow() {
        Warehouse destination = mock(Warehouse.class);
        Material material = mock(Material.class);
        WarehouseRequestDetail requestDetail = mock(WarehouseRequestDetail.class);
        when(requestDetail.getMaterial()).thenReturn(material);
        when(requestDetail.getCondition()).thenReturn(MaterialCondition.NEW);
        when(requestDetail.getQuantity()).thenReturn(8L);
        WarehouseTransactionDetail detail = mock(WarehouseTransactionDetail.class);
        when(detail.getRequestDetail()).thenReturn(requestDetail);
        WarehouseRequest request = processingRequest(OperationDirection.IMPORT, false);
        when(request.getDestinationWarehouse()).thenReturn(destination);
        WarehouseTransaction transaction = readyTransaction(request, List.of(detail));

        service.confirm(7L, 99L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryLot>> lots = ArgumentCaptor.forClass(List.class);
        verify(lotRepository).saveAllAndFlush(lots.capture());
        assertThat(lots.getValue()).hasSize(1);
        assertThat(lots.getValue().getFirst().getOnHandQuantity()).isEqualTo(8L);
        assertThat(lots.getValue().getFirst().getReservedQuantity()).isZero();
        assertThat(lots.getValue().getFirst().getWarehouse()).isSameAs(destination);
        verify(transaction).confirmPhysical(any(AppUser.class), any(Instant.class),
                org.mockito.ArgumentMatchers.eq(false));
        verify(request).complete();
        verify(historyRepository, times(2)).save(any(StatusHistory.class));
    }

    @Test
    void confirmPoImportUpdatesReceiptMetadataAndCompletesPoWhenFullyReceived() {
        PurchaseOrder purchaseOrder = mock(PurchaseOrder.class);
        PurchaseOrderItem item = mock(PurchaseOrderItem.class);
        when(purchaseOrder.getId()).thenReturn(31L);
        when(purchaseOrder.getStatus()).thenReturn(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        when(purchaseOrder.getItems()).thenReturn(List.of(item));
        when(item.getId()).thenReturn(41L);
        when(item.getPurchaseOrder()).thenReturn(purchaseOrder);
        when(item.getOrderedQuantity()).thenReturn(5L);
        WarehouseRequestDetail requestDetail = mock(WarehouseRequestDetail.class);
        when(requestDetail.getPurchaseOrderItem()).thenReturn(item);
        when(requestDetail.getMaterial()).thenReturn(mock(Material.class));
        when(requestDetail.getCondition()).thenReturn(MaterialCondition.NEW);
        when(requestDetail.getQuantity()).thenReturn(3L);
        WarehouseTransactionDetail detail = mock(WarehouseTransactionDetail.class);
        when(detail.getRequestDetail()).thenReturn(requestDetail);
        WarehouseRequest request = processingRequest(OperationDirection.IMPORT, true);
        when(request.getDestinationWarehouse()).thenReturn(mock(Warehouse.class));
        when(request.getPurchaseOrder()).thenReturn(purchaseOrder);
        readyTransaction(request, List.of(detail));
        when(purchaseOrderRepository.findByIdForUpdate(31L))
                .thenReturn(Optional.of(purchaseOrder));
        when(itemRepository.findAllByIdForUpdate(List.of(41L))).thenReturn(List.of(item));
        when(itemRepository.receivedQuantity(41L)).thenReturn(2L, 5L);

        service.confirm(7L, 99L);

        verify(purchaseOrder).registerReceipt(any(Instant.class),
                org.mockito.ArgumentMatchers.eq(true));
        verify(purchaseOrderRepository).saveAndFlush(purchaseOrder);
    }

    @Test
    void confirmExportConsumesApprovedAllocationWithoutRerunningFifo() {
        InventoryLot allocatedLot = mock(InventoryLot.class);
        InventoryLot lockedLot = mock(InventoryLot.class);
        when(allocatedLot.getId()).thenReturn(11L);
        when(lockedLot.getId()).thenReturn(11L);
        IssueLotAllocation allocation = mock(IssueLotAllocation.class);
        when(allocation.getInventoryLot()).thenReturn(allocatedLot);
        when(allocation.getAllocatedQuantity()).thenReturn(4L);
        WarehouseTransactionDetail detail = mock(WarehouseTransactionDetail.class);
        when(detail.getAllocations()).thenReturn(List.of(allocation));
        WarehouseRequest request = processingRequest(OperationDirection.EXPORT, true);
        WarehouseTransaction transaction = readyTransaction(request, List.of(detail));
        when(lotRepository.findAllByIdForUpdate(List.of(11L))).thenReturn(List.of(lockedLot));

        service.confirm(7L, 99L);

        verify(lockedLot).issue(4L);
        verify(lotRepository).flush();
        verify(lotRepository, never()).saveAllAndFlush(any());
        verify(transaction).confirmPhysical(any(AppUser.class), any(Instant.class),
                org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void confirmRejectsRepeatedDecisionBeforeInventoryMutation() {
        WarehouseTransaction transaction = mock(WarehouseTransaction.class);
        when(transaction.getStatus()).thenReturn(WarehouseTransactionStatus.COMPLETED);
        when(transactionRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.confirm(7L, 99L))
                .isInstanceOf(WarehouseTransactionConflictException.class);

        verify(lotRepository, never()).flush();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void confirmRejectsRequestOutsideProcessingState() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        when(request.getId()).thenReturn(5L);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.CANCELLED);
        WarehouseTransaction transaction = mock(WarehouseTransaction.class);
        when(transaction.getStatus()).thenReturn(WarehouseTransactionStatus.READY_FOR_CONFIRMATION);
        when(transaction.getRequest()).thenReturn(request);
        when(transactionRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transaction));
        when(requestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.confirm(7L, 99L))
                .isInstanceOf(WarehouseTransactionConflictException.class);

        verify(lotRepository, never()).flush();
    }

    private WarehouseTransaction readyTransaction(WarehouseRequest request,
            List<WarehouseTransactionDetail> details) {
        WarehouseTransaction transaction = mock(WarehouseTransaction.class);
        when(transaction.getId()).thenReturn(7L);
        when(transaction.getStatus()).thenReturn(WarehouseTransactionStatus.READY_FOR_CONFIRMATION);
        when(transaction.getRequest()).thenReturn(request);
        when(transaction.getDetails()).thenReturn(details);
        when(transactionRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transaction));
        return transaction;
    }

    private WarehouseRequest processingRequest(OperationDirection direction,
            boolean requiresAccounting) {
        WarehouseRequest request = mock(WarehouseRequest.class);
        OperationType operationType = mock(OperationType.class);
        when(request.getId()).thenReturn(5L);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.PROCESSING);
        when(request.getOperationType()).thenReturn(operationType);
        when(operationType.getDirection()).thenReturn(direction);
        when(operationType.isRequiresAccounting()).thenReturn(requiresAccounting);
        when(requestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(userRepository.findById(99L)).thenReturn(Optional.of(mock(AppUser.class)));
        return request;
    }
}
