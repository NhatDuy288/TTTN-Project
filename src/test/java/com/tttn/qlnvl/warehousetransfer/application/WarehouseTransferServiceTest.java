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
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransfer;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class WarehouseTransferServiceTest {
    private WarehouseTransferRepository transferRepository;
    private StatusHistoryRepository historyRepository;
    private AppUserRepository userRepository;
    private WarehouseTransferService service;

    @BeforeEach
    void setUp() {
        transferRepository = mock(WarehouseTransferRepository.class);
        historyRepository = mock(StatusHistoryRepository.class);
        userRepository = mock(AppUserRepository.class);
        service = new WarehouseTransferService(transferRepository, historyRepository,
                userRepository);
    }

    @Test
    void queueUsesRequestedStatusAndAcceptedPagination() {
        when(transferRepository.findQueue(any(), any())).thenReturn(Page.empty());

        service.queue(-2, 50, WarehouseTransferStatus.SUBMITTED);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transferRepository).findQueue(
                org.mockito.ArgumentMatchers.eq(WarehouseTransferStatus.SUBMITTED),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("updatedAt").isDescending()).isTrue();
    }

    @Test
    void queueFallsBackToDraftAndTwentyRows() {
        when(transferRepository.findQueue(any(), any())).thenReturn(Page.empty());

        service.queue(0, 15, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transferRepository).findQueue(
                org.mockito.ArgumentMatchers.eq(WarehouseTransferStatus.DRAFT),
                pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void submitMovesDraftTransferAndWritesSingleHistory() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.PROCESSING);
        WarehouseTransfer transfer = mock(WarehouseTransfer.class);
        when(transfer.getId()).thenReturn(7L);
        when(transfer.getStatus()).thenReturn(WarehouseTransferStatus.DRAFT);
        when(transfer.getRequest()).thenReturn(request);
        when(transferRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transfer));
        when(userRepository.findById(99L)).thenReturn(Optional.of(mock(AppUser.class)));

        service.submit(7L, 99L);

        verify(transfer).submit();
        verify(transferRepository).saveAndFlush(transfer);
        verify(historyRepository).save(any(StatusHistory.class));
    }

    @Test
    void submitRejectsTransferOutsideDraftState() {
        WarehouseTransfer transfer = mock(WarehouseTransfer.class);
        when(transfer.getStatus()).thenReturn(WarehouseTransferStatus.SUBMITTED);
        when(transferRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.submit(7L, 99L))
                .isInstanceOf(WarehouseTransferConflictException.class);

        verify(transfer, never()).submit();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void submitRejectsTransferWhoseRequestIsNotProcessing() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.CANCELLED);
        WarehouseTransfer transfer = mock(WarehouseTransfer.class);
        when(transfer.getStatus()).thenReturn(WarehouseTransferStatus.DRAFT);
        when(transfer.getRequest()).thenReturn(request);
        when(transferRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.submit(7L, 99L))
                .isInstanceOf(WarehouseTransferConflictException.class);

        verify(transfer, never()).submit();
        verify(historyRepository, never()).save(any());
    }
}
