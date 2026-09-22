package com.tttn.qlnvl.shared.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.domain.Role;
import com.tttn.qlnvl.warehouserequest.application.RequestApprovalService;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestNotFoundException;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestService;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehousetransaction.application.TransactionApprovalService;
import com.tttn.qlnvl.warehousetransaction.application.TransactionConfirmationService;
import com.tttn.qlnvl.warehousetransaction.application.WarehouseTransactionService;
import com.tttn.qlnvl.warehousetransfer.application.TransferApprovalService;
import com.tttn.qlnvl.warehousetransfer.application.TransferConfirmationService;
import com.tttn.qlnvl.warehousetransfer.application.WarehouseTransferService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StatusHistoryServiceTest {
    private final StatusHistoryRepository history = mock(StatusHistoryRepository.class);
    private final WarehouseRequestService requests = mock(WarehouseRequestService.class);
    private final RequestApprovalService requestApprovals = mock(RequestApprovalService.class);
    private final WarehouseTransactionService transactions = mock(WarehouseTransactionService.class);
    private final TransactionApprovalService transactionApprovals = mock(TransactionApprovalService.class);
    private final TransactionConfirmationService transactionConfirmations = mock(TransactionConfirmationService.class);
    private final WarehouseTransferService transfers = mock(WarehouseTransferService.class);
    private final TransferApprovalService transferApprovals = mock(TransferApprovalService.class);
    private final TransferConfirmationService transferConfirmations = mock(TransferConfirmationService.class);
    private StatusHistoryService service;

    @BeforeEach
    void setUp() {
        service = new StatusHistoryService(history, requests, requestApprovals,
                transactions, transactionApprovals, transactionConfirmations,
                transfers, transferApprovals, transferConfirmations);
    }

    @Test
    void requesterReadsOnlyOwnRequestAndNoSyntheticEvent() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        when(requests.getOwned(7L, 11L)).thenReturn(request);
        when(request.getRequestCode()).thenReturn("REQ-7");
        when(history.findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(AggregateType.REQUEST, 7L))
                .thenReturn(List.of());

        StatusHistoryService.Timeline timeline = service.get(AggregateType.REQUEST, 7L,
                principal(11L, Role.REQUESTER));

        assertEquals("REQ-7", timeline.title());
        assertEquals("/requests/7", timeline.backUrl());
        assertEquals(List.of(), timeline.events());
        verify(requests).getOwned(7L, 11L);
    }

    @Test
    void inaccessibleRequestDoesNotLeakHistory() {
        when(requests.getOwned(7L, 11L)).thenThrow(new WarehouseRequestNotFoundException());

        assertThrows(WarehouseRequestNotFoundException.class,
                () -> service.get(AggregateType.REQUEST, 7L, principal(11L, Role.REQUESTER)));
        verify(history, never()).findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(AggregateType.REQUEST, 7L);
    }

    @Test
    void unrelatedRoleCannotReadTransactionHistory() {
        assertThrows(AccessDeniedException.class,
                () -> service.get(AggregateType.TRANSACTION, 7L, principal(11L, Role.REQUESTER)));
        verify(history, never()).findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(AggregateType.TRANSACTION, 7L);
    }

    private AppUserPrincipal principal(Long id, Role role) {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        return AppUserPrincipal.from(user);
    }
}
