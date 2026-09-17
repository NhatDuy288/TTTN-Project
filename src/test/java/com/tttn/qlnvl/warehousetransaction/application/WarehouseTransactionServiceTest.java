package com.tttn.qlnvl.warehousetransaction.application;

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
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class WarehouseTransactionServiceTest {
    private WarehouseTransactionRepository transactionRepository;
    private StatusHistoryRepository historyRepository;
    private AppUserRepository userRepository;
    private WarehouseTransactionService service;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(WarehouseTransactionRepository.class);
        historyRepository = mock(StatusHistoryRepository.class);
        userRepository = mock(AppUserRepository.class);
        service = new WarehouseTransactionService(transactionRepository, historyRepository,
                userRepository);
    }

    @Test
    void queueUsesRequestedStatusAndAcceptedPagination() {
        when(transactionRepository.findQueue(any(), any())).thenReturn(Page.empty());

        service.queue(-2, 50, WarehouseTransactionStatus.SUBMITTED);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findQueue(
                org.mockito.ArgumentMatchers.eq(WarehouseTransactionStatus.SUBMITTED),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("updatedAt").isDescending()).isTrue();
    }

    @Test
    void queueFallsBackToDraftAndTwentyRows() {
        when(transactionRepository.findQueue(any(), any())).thenReturn(Page.empty());

        service.queue(0, 15, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findQueue(
                org.mockito.ArgumentMatchers.eq(WarehouseTransactionStatus.DRAFT),
                pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void submitTrimsExecutionNoteAndWritesTransactionHistory() {
        WarehouseTransaction transaction = mock(WarehouseTransaction.class);
        AppUser actor = mock(AppUser.class);
        when(transaction.getId()).thenReturn(7L);
        when(transaction.getStatus()).thenReturn(WarehouseTransactionStatus.DRAFT);
        when(transactionRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transaction));
        when(userRepository.findById(99L)).thenReturn(Optional.of(actor));

        service.submit(7L, 99L, "  Đã kiểm tra chứng từ  ");

        verify(transaction).submit("Đã kiểm tra chứng từ");
        verify(transactionRepository).saveAndFlush(transaction);
        verify(historyRepository).save(any(StatusHistory.class));
    }

    @Test
    void submitRejectsNoteLongerThanOneThousandCharactersWithoutMutation() {
        WarehouseTransaction transaction = mock(WarehouseTransaction.class);
        when(transaction.getStatus()).thenReturn(WarehouseTransactionStatus.DRAFT);
        when(transactionRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.submit(7L, 99L, "x".repeat(1001)))
                .isInstanceOf(InvalidWarehouseTransactionException.class);

        verify(transaction, never()).submit(any());
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void submitRejectsNonDraftTransactionWithoutMutation() {
        WarehouseTransaction transaction = mock(WarehouseTransaction.class);
        when(transaction.getStatus()).thenReturn(WarehouseTransactionStatus.SUBMITTED);
        when(transactionRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.submit(7L, 99L, null))
                .isInstanceOf(WarehouseTransactionConflictException.class);

        verify(transaction, never()).submit(any());
        verify(userRepository, never()).findById(any());
        verify(historyRepository, never()).save(any());
    }
}
