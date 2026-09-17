package com.tttn.qlnvl.warehousetransfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestDetail;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import com.tttn.qlnvl.warehousetransfer.domain.TransferLotAllocation;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransfer;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferDetail;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class TransferConfirmationServiceTest {
    private WarehouseTransferRepository transferRepository;
    private WarehouseRequestRepository requestRepository;
    private InventoryLotRepository lotRepository;
    private StatusHistoryRepository historyRepository;
    private AppUserRepository userRepository;
    private TransferConfirmationService service;

    @BeforeEach
    void setUp() {
        transferRepository = mock(WarehouseTransferRepository.class);
        requestRepository = mock(WarehouseRequestRepository.class);
        lotRepository = mock(InventoryLotRepository.class);
        historyRepository = mock(StatusHistoryRepository.class);
        userRepository = mock(AppUserRepository.class);
        service = new TransferConfirmationService(transferRepository, requestRepository,
                lotRepository, historyRepository, userRepository);
    }

    @Test
    void sourceQueueUsesReadyToTransferAndSafePagination() {
        when(transferRepository.findQueue(any(), any())).thenReturn(Page.empty());

        service.sourceQueue(-1, 50);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transferRepository).findQueue(
                org.mockito.ArgumentMatchers.eq(WarehouseTransferStatus.READY_TO_TRANSFER),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void confirmSourceIssuesApprovedAllocationAndKeepsRequestProcessing() {
        WarehouseRequest request = processingRequest();
        WarehouseTransferDetail detail = allocatedDetail(11L, 4L, 4L);
        WarehouseTransfer transfer = readyTransfer(request, List.of(detail));
        InventoryLot lockedLot = mock(InventoryLot.class);
        when(lockedLot.getId()).thenReturn(11L);
        when(lotRepository.findAllByIdForUpdate(List.of(11L))).thenReturn(List.of(lockedLot));

        service.confirmSource(7L, 99L);

        verify(lockedLot).issue(4L);
        verify(lotRepository).flush();
        verify(transfer).confirmSource(any(AppUser.class), any(Instant.class));
        verify(transferRepository).saveAndFlush(transfer);
        verify(request, never()).complete();
        verify(requestRepository, never()).saveAndFlush(any());
        verify(historyRepository).save(any(StatusHistory.class));
    }

    @Test
    void confirmSourceRejectsRepeatedConfirmationBeforeInventoryMutation() {
        WarehouseTransfer transfer = mock(WarehouseTransfer.class);
        when(transfer.getStatus()).thenReturn(WarehouseTransferStatus.IN_TRANSIT);
        when(transferRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.confirmSource(7L, 99L))
                .isInstanceOf(WarehouseTransferConflictException.class);

        verify(lotRepository, never()).flush();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void confirmSourceRejectsRequestOutsideProcessingState() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        when(request.getId()).thenReturn(5L);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.CANCELLED);
        readyTransfer(request, List.of());
        when(requestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.confirmSource(7L, 99L))
                .isInstanceOf(WarehouseTransferConflictException.class);

        verify(lotRepository, never()).flush();
    }

    @Test
    void confirmSourceRejectsIncompleteAllocation() {
        WarehouseRequest request = processingRequest();
        WarehouseTransferDetail detail = allocatedDetail(11L, 3L, 4L);
        WarehouseTransfer transfer = readyTransfer(request, List.of(detail));

        assertThatThrownBy(() -> service.confirmSource(7L, 99L))
                .isInstanceOf(WarehouseTransferConflictException.class);

        verify(lotRepository, never()).findAllByIdForUpdate(any());
        verify(transfer, never()).confirmSource(any(), any());
    }

    @Test
    void confirmSourceRejectsMissingLockedLot() {
        WarehouseRequest request = processingRequest();
        WarehouseTransferDetail detail = allocatedDetail(11L, 4L, 4L);
        WarehouseTransfer transfer = readyTransfer(request, List.of(detail));
        when(lotRepository.findAllByIdForUpdate(List.of(11L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.confirmSource(7L, 99L))
                .isInstanceOf(WarehouseTransferConflictException.class);

        verify(transfer, never()).confirmSource(any(), any());
        verify(lotRepository, never()).flush();
    }

    private WarehouseRequest processingRequest() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        when(request.getId()).thenReturn(5L);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.PROCESSING);
        when(requestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(userRepository.findById(99L)).thenReturn(Optional.of(mock(AppUser.class)));
        return request;
    }

    private WarehouseTransfer readyTransfer(WarehouseRequest request,
            List<WarehouseTransferDetail> details) {
        WarehouseTransfer transfer = mock(WarehouseTransfer.class);
        when(transfer.getId()).thenReturn(7L);
        when(transfer.getStatus()).thenReturn(WarehouseTransferStatus.READY_TO_TRANSFER);
        when(transfer.getRequest()).thenReturn(request);
        when(transfer.getDetails()).thenReturn(details);
        when(transferRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transfer));
        return transfer;
    }

    private WarehouseTransferDetail allocatedDetail(Long lotId, long allocatedQuantity,
            long requestedQuantity) {
        InventoryLot allocatedLot = mock(InventoryLot.class);
        when(allocatedLot.getId()).thenReturn(lotId);
        TransferLotAllocation allocation = mock(TransferLotAllocation.class);
        when(allocation.getInventoryLot()).thenReturn(allocatedLot);
        when(allocation.getAllocatedQuantity()).thenReturn(allocatedQuantity);
        WarehouseRequestDetail requestDetail = mock(WarehouseRequestDetail.class);
        when(requestDetail.getQuantity()).thenReturn(requestedQuantity);
        WarehouseTransferDetail detail = mock(WarehouseTransferDetail.class);
        when(detail.getRequestDetail()).thenReturn(requestDetail);
        when(detail.getAllocations()).thenReturn(List.of(allocation));
        return detail;
    }
}
