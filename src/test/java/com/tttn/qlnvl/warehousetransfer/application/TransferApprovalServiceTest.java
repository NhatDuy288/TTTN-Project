package com.tttn.qlnvl.warehousetransfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import com.tttn.qlnvl.warehousetransfer.domain.TransferLotAllocation;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransfer;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferDetail;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class TransferApprovalServiceTest {
    private WarehouseTransferRepository transferRepository;
    private WarehouseRequestRepository requestRepository;
    private InventoryLotRepository lotRepository;
    private StatusHistoryRepository historyRepository;
    private AppUserRepository userRepository;
    private TransferApprovalService service;

    @BeforeEach
    void setUp() {
        transferRepository = mock(WarehouseTransferRepository.class);
        requestRepository = mock(WarehouseRequestRepository.class);
        lotRepository = mock(InventoryLotRepository.class);
        historyRepository = mock(StatusHistoryRepository.class);
        userRepository = mock(AppUserRepository.class);
        service = new TransferApprovalService(transferRepository, requestRepository,
                lotRepository, historyRepository, userRepository);
    }

    @Test
    void queueAlwaysUsesSubmittedAndSafePagination() {
        when(transferRepository.findQueue(any(), any())).thenReturn(Page.empty());

        service.queue(-1, 50);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transferRepository).findQueue(
                org.mockito.ArgumentMatchers.eq(WarehouseTransferStatus.SUBMITTED),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void approveMovesOnlyTransferAndKeepsReservations() {
        WarehouseRequest request = processingRequest();
        WarehouseTransfer transfer = submittedTransfer(request);
        when(userRepository.findById(99L)).thenReturn(Optional.of(mock(AppUser.class)));

        service.approve(7L, 99L, "  Đồng ý  ");

        verify(transfer).approve();
        verify(transferRepository).saveAndFlush(transfer);
        verify(request, never()).cancel();
        verify(requestRepository, never()).saveAndFlush(any());
        verify(lotRepository, never()).findAllByIdForUpdate(any());
        verify(historyRepository).save(any(StatusHistory.class));
    }

    @Test
    void rejectCancelsRequestAndReleasesAllocatedLotsWithoutIssuingStock() {
        WarehouseRequest request = processingRequest();
        WarehouseTransfer transfer = submittedTransfer(request);
        WarehouseTransferDetail detail = mock(WarehouseTransferDetail.class);
        TransferLotAllocation allocation = mock(TransferLotAllocation.class);
        InventoryLot allocatedLot = mock(InventoryLot.class);
        InventoryLot lockedLot = mock(InventoryLot.class);
        when(allocatedLot.getId()).thenReturn(11L);
        when(lockedLot.getId()).thenReturn(11L);
        when(allocation.getInventoryLot()).thenReturn(allocatedLot);
        when(allocation.getAllocatedQuantity()).thenReturn(4L);
        when(detail.getAllocations()).thenReturn(List.of(allocation));
        when(transfer.getDetails()).thenReturn(List.of(detail));
        when(lotRepository.findAllByIdForUpdate(List.of(11L))).thenReturn(List.of(lockedLot));
        when(userRepository.findById(99L)).thenReturn(Optional.of(mock(AppUser.class)));

        service.reject(7L, 99L, "Không đạt");

        verify(lockedLot).releaseReservation(4L);
        verify(lockedLot, never()).issue(anyLong());
        verify(lotRepository).flush();
        verify(transfer).reject();
        verify(request).cancel();
        verify(historyRepository, times(2)).save(any(StatusHistory.class));
    }

    @Test
    void rejectStopsWhenAllocationIsMissing() {
        WarehouseRequest request = processingRequest();
        WarehouseTransfer transfer = submittedTransfer(request);
        when(transfer.getDetails()).thenReturn(List.of());
        when(userRepository.findById(99L)).thenReturn(Optional.of(mock(AppUser.class)));

        assertThatThrownBy(() -> service.reject(7L, 99L, null))
                .isInstanceOf(WarehouseTransferConflictException.class);

        verify(transfer, never()).reject();
        verify(request, never()).cancel();
    }

    @Test
    void decisionStopsWhenRequestIsNotProcessing() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        when(request.getId()).thenReturn(5L);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.CANCELLED);
        submittedTransfer(request);
        when(requestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(7L, 99L, null))
                .isInstanceOf(WarehouseTransferConflictException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void decisionRejectsCommentLongerThanFiveHundredCharacters() {
        WarehouseRequest request = processingRequest();
        WarehouseTransfer transfer = submittedTransfer(request);

        assertThatThrownBy(() -> service.approve(7L, 99L, "x".repeat(501)))
                .isInstanceOf(InvalidWarehouseTransferException.class);

        verify(transfer, never()).approve();
        verify(transferRepository, never()).saveAndFlush(any());
    }

    @Test
    void decisionRejectsTransferOutsideSubmittedState() {
        WarehouseTransfer transfer = mock(WarehouseTransfer.class);
        when(transfer.getStatus()).thenReturn(WarehouseTransferStatus.READY_TO_TRANSFER);
        when(transferRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.approve(7L, 99L, null))
                .isInstanceOf(WarehouseTransferConflictException.class);

        verify(requestRepository, never()).findByIdForUpdate(any());
    }

    private WarehouseTransfer submittedTransfer(WarehouseRequest request) {
        WarehouseTransfer transfer = mock(WarehouseTransfer.class);
        when(transfer.getId()).thenReturn(7L);
        when(transfer.getStatus()).thenReturn(WarehouseTransferStatus.SUBMITTED);
        when(transfer.getRequest()).thenReturn(request);
        when(transferRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transfer));
        return transfer;
    }

    private WarehouseRequest processingRequest() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        when(request.getId()).thenReturn(5L);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.PROCESSING);
        when(requestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        return request;
    }
}
