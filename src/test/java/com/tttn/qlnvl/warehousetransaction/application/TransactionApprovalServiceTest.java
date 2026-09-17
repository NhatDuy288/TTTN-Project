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
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import com.tttn.qlnvl.warehousetransaction.domain.IssueLotAllocation;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionDetail;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class TransactionApprovalServiceTest {
    private WarehouseTransactionRepository transactionRepository;
    private WarehouseRequestRepository requestRepository;
    private InventoryLotRepository lotRepository;
    private StatusHistoryRepository historyRepository;
    private AppUserRepository userRepository;
    private TransactionApprovalService service;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(WarehouseTransactionRepository.class);
        requestRepository = mock(WarehouseRequestRepository.class);
        lotRepository = mock(InventoryLotRepository.class);
        historyRepository = mock(StatusHistoryRepository.class);
        userRepository = mock(AppUserRepository.class);
        service = new TransactionApprovalService(transactionRepository, requestRepository,
                lotRepository, historyRepository, userRepository);
    }

    @Test
    void queueAlwaysUsesSubmittedAndSafePagination() {
        when(transactionRepository.findQueue(any(), any())).thenReturn(Page.empty());

        service.queue(-1, 50);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findQueue(
                org.mockito.ArgumentMatchers.eq(WarehouseTransactionStatus.SUBMITTED),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void approveMovesOnlyTransactionAndWritesHistory() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        WarehouseTransaction transaction = submittedTransaction(request);
        AppUser actor = mock(AppUser.class);
        when(userRepository.findById(99L)).thenReturn(Optional.of(actor));

        service.approve(7L, 99L, "  Đồng ý  ");

        verify(transaction).approve();
        verify(transactionRepository).saveAndFlush(transaction);
        verify(requestRepository, never()).saveAndFlush(any());
        verify(lotRepository, never()).flush();
        verify(historyRepository).save(any(StatusHistory.class));
    }

    @Test
    void rejectImportCancelsRequestWithoutTouchingInventoryLots() {
        WarehouseRequest request = processingRequest(OperationDirection.IMPORT);
        WarehouseTransaction transaction = submittedTransaction(request);
        when(request.getId()).thenReturn(5L);
        when(requestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(userRepository.findById(99L)).thenReturn(Optional.of(mock(AppUser.class)));

        service.reject(7L, 99L, null);

        verify(transaction).reject();
        verify(request).cancel();
        verify(lotRepository, never()).findAllByIdForUpdate(any());
        verify(historyRepository, times(2)).save(any(StatusHistory.class));
    }

    @Test
    void rejectExportReleasesAllocatedLotsAndKeepsOnHandUntouched() {
        WarehouseRequest request = processingRequest(OperationDirection.EXPORT);
        when(request.getId()).thenReturn(5L);
        WarehouseTransaction transaction = submittedTransaction(request);
        WarehouseTransactionDetail detail = mock(WarehouseTransactionDetail.class);
        IssueLotAllocation allocation = mock(IssueLotAllocation.class);
        InventoryLot allocatedLot = mock(InventoryLot.class);
        InventoryLot lockedLot = mock(InventoryLot.class);
        when(allocatedLot.getId()).thenReturn(11L);
        when(lockedLot.getId()).thenReturn(11L);
        when(allocation.getInventoryLot()).thenReturn(allocatedLot);
        when(allocation.getAllocatedQuantity()).thenReturn(4L);
        when(detail.getAllocations()).thenReturn(List.of(allocation));
        when(transaction.getDetails()).thenReturn(List.of(detail));
        when(requestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(userRepository.findById(99L)).thenReturn(Optional.of(mock(AppUser.class)));
        when(lotRepository.findAllByIdForUpdate(List.of(11L))).thenReturn(List.of(lockedLot));

        service.reject(7L, 99L, "Không đạt");

        verify(lockedLot).releaseReservation(4L);
        verify(lotRepository).flush();
        verify(transaction).reject();
        verify(request).cancel();
    }

    @Test
    void rejectStopsWhenRequestIsNotProcessing() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        when(request.getId()).thenReturn(5L);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.CANCELLED);
        WarehouseTransaction transaction = submittedTransaction(request);
        when(requestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.reject(7L, 99L, null))
                .isInstanceOf(WarehouseTransactionConflictException.class);

        verify(transaction, never()).reject();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void decisionRejectsCommentLongerThanFiveHundredCharacters() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        WarehouseTransaction transaction = submittedTransaction(request);

        assertThatThrownBy(() -> service.approve(7L, 99L, "x".repeat(501)))
                .isInstanceOf(InvalidWarehouseTransactionException.class);

        verify(transaction, never()).approve();
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void decisionRejectsTransactionOutsideSubmittedState() {
        WarehouseTransaction transaction = mock(WarehouseTransaction.class);
        when(transaction.getStatus()).thenReturn(WarehouseTransactionStatus.READY_FOR_CONFIRMATION);
        when(transactionRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.approve(7L, 99L, null))
                .isInstanceOf(WarehouseTransactionConflictException.class);

        verify(userRepository, never()).findById(any());
    }

    private WarehouseTransaction submittedTransaction(WarehouseRequest request) {
        WarehouseTransaction transaction = mock(WarehouseTransaction.class);
        when(transaction.getId()).thenReturn(7L);
        when(transaction.getStatus()).thenReturn(WarehouseTransactionStatus.SUBMITTED);
        when(transaction.getRequest()).thenReturn(request);
        when(transactionRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transaction));
        return transaction;
    }

    private WarehouseRequest processingRequest(OperationDirection direction) {
        WarehouseRequest request = mock(WarehouseRequest.class);
        OperationType operationType = mock(OperationType.class);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.PROCESSING);
        when(request.getOperationType()).thenReturn(operationType);
        when(operationType.getDirection()).thenReturn(direction);
        return request;
    }
}
