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
import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import com.tttn.qlnvl.warehouserequest.domain.StockReservation;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestDetail;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.StockReservationRepository;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransfer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RequestApprovalServiceTest {
    private WarehouseRequestRepository requestRepository;
    private StockReservationRepository reservationRepository;
    private InventoryLotRepository lotRepository;
    private WarehouseTransactionRepository transactionRepository;
    private WarehouseTransferRepository transferRepository;
    private StatusHistoryRepository historyRepository;
    private AppUserRepository userRepository;
    private RequestApprovalService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(WarehouseRequestRepository.class);
        reservationRepository = mock(StockReservationRepository.class);
        lotRepository = mock(InventoryLotRepository.class);
        transactionRepository = mock(WarehouseTransactionRepository.class);
        transferRepository = mock(WarehouseTransferRepository.class);
        historyRepository = mock(StatusHistoryRepository.class);
        userRepository = mock(AppUserRepository.class);
        service = new RequestApprovalService(requestRepository, reservationRepository,
                lotRepository, transactionRepository, transferRepository, historyRepository,
                userRepository);
    }

    @Test
    void approvesImportAndCreatesDraftTransactionWithoutReservationConversion() {
        WarehouseRequest request = request(OperationDirection.IMPORT, "INP_20260916_001", List.of());
        prepareLocked(request);

        service.approve(7L, 99L, "  Đồng ý  ");

        ArgumentCaptor<WarehouseTransaction> transaction =
                ArgumentCaptor.forClass(WarehouseTransaction.class);
        verify(transactionRepository).saveAndFlush(transaction.capture());
        assertThat(transaction.getValue().getTransactionCode()).isEqualTo("GRN_INP_20260916_001");
        verify(reservationRepository, never()).findActiveByRequestIdForUpdate(any());
        verify(request).approve();
        verify(request).startProcessing();
        verify(historyRepository, org.mockito.Mockito.times(2)).save(any(StatusHistory.class));
    }

    @Test
    void approvesExportByAllocatingFifoAcrossLotsAndConvertingSoftReservation() {
        WarehouseRequestDetail detail = detail(11L, 20L, 5L);
        WarehouseRequest request = request(OperationDirection.EXPORT, "OUT_20260916_001",
                List.of(detail));
        StockReservation reservation = mock(StockReservation.class);
        when(reservation.getRequestDetail()).thenReturn(detail);
        when(reservation.getQuantity()).thenReturn(5L);
        InventoryLot first = lot(3L, 0L, "10.0000");
        InventoryLot second = lot(9L, 7L, "12.0000");
        prepareLocked(request);
        when(reservationRepository.findActiveByRequestIdForUpdate(7L))
                .thenReturn(List.of(reservation));
        when(lotRepository.findAvailableFifoForUpdate(10L, 20L, MaterialCondition.NEW))
                .thenReturn(List.of(first, second));

        service.approve(7L, 99L, null);

        verify(first).reserve(3L);
        verify(second).reserve(2L);
        verify(reservation).convert();
        ArgumentCaptor<WarehouseTransaction> transaction =
                ArgumentCaptor.forClass(WarehouseTransaction.class);
        verify(transactionRepository).saveAndFlush(transaction.capture());
        assertThat(transaction.getValue().getDetails().get(0).getAllocations()).hasSize(2);
        verify(request).approve();
        verify(request).startProcessing();
    }

    @Test
    void rejectsSubmittedExportAndReleasesSoftReservationWithoutExecution() {
        WarehouseRequest request = request(OperationDirection.EXPORT, "OUT_20260916_001", List.of());
        StockReservation reservation = mock(StockReservation.class);
        prepareLocked(request);
        when(reservationRepository.findActiveByRequestIdForUpdate(7L))
                .thenReturn(List.of(reservation));

        service.reject(7L, 99L, "Không phù hợp");

        verify(reservation).release();
        verify(request).reject();
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(transferRepository, never()).saveAndFlush(any());
        verify(historyRepository).save(any(StatusHistory.class));
    }

    @Test
    void approvesTransferByCreatingSingleTransferWithFifoAllocation() {
        WarehouseRequestDetail detail = detail(11L, 20L, 4L);
        WarehouseRequest request = request(OperationDirection.TRANSFER, "TRF_20260916_001",
                List.of(detail));
        Warehouse destination = mock(Warehouse.class);
        when(request.getDestinationWarehouse()).thenReturn(destination);
        StockReservation reservation = mock(StockReservation.class);
        when(reservation.getRequestDetail()).thenReturn(detail);
        when(reservation.getQuantity()).thenReturn(4L);
        InventoryLot lot = lot(10L, 1L, "15.0000");
        prepareLocked(request);
        when(reservationRepository.findActiveByRequestIdForUpdate(7L))
                .thenReturn(List.of(reservation));
        when(lotRepository.findAvailableFifoForUpdate(10L, 20L, MaterialCondition.NEW))
                .thenReturn(List.of(lot));

        service.approve(7L, 99L, null);

        ArgumentCaptor<WarehouseTransfer> transfer = ArgumentCaptor.forClass(WarehouseTransfer.class);
        verify(transferRepository).saveAndFlush(transfer.capture());
        assertThat(transfer.getValue().getDetails()).hasSize(1);
        assertThat(transfer.getValue().getDetails().get(0).getAllocations()).hasSize(1);
        verify(lot).reserve(4L);
        verify(reservation).convert();
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsApprovalCommentLongerThanAcceptedLimitBeforeMutation() {
        WarehouseRequest request = request(OperationDirection.IMPORT, "INP_20260916_001", List.of());
        prepareLocked(request);

        assertThatThrownBy(() -> service.approve(7L, 99L, "x".repeat(501)))
                .isInstanceOf(InvalidWarehouseRequestException.class);

        verify(request, never()).approve();
        verify(request, never()).startProcessing();
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void insufficientFifoStockDoesNotMoveRequestToApprovedOrProcessing() {
        WarehouseRequestDetail detail = detail(11L, 20L, 5L);
        WarehouseRequest request = request(OperationDirection.EXPORT, "OUT_20260916_001",
                List.of(detail));
        StockReservation reservation = mock(StockReservation.class);
        when(reservation.getRequestDetail()).thenReturn(detail);
        when(reservation.getQuantity()).thenReturn(5L);
        prepareLocked(request);
        when(reservationRepository.findActiveByRequestIdForUpdate(7L))
                .thenReturn(List.of(reservation));
        InventoryLot insufficientLot = lot(2L, 0L, "10.0000");
        when(lotRepository.findAvailableFifoForUpdate(10L, 20L, MaterialCondition.NEW))
                .thenReturn(List.of(insufficientLot));

        assertThatThrownBy(() -> service.approve(7L, 99L, null))
                .isInstanceOf(WarehouseRequestConflictException.class);

        verify(request, never()).approve();
        verify(request, never()).startProcessing();
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    private void prepareLocked(WarehouseRequest request) {
        when(requestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));
        when(userRepository.findById(99L)).thenReturn(Optional.of(mock(AppUser.class)));
    }

    private WarehouseRequest request(OperationDirection direction, String code,
            List<WarehouseRequestDetail> details) {
        WarehouseRequest request = mock(WarehouseRequest.class);
        OperationType operation = mock(OperationType.class);
        Warehouse source = mock(Warehouse.class);
        when(request.getId()).thenReturn(7L);
        when(request.getStatus()).thenReturn(WarehouseRequestStatus.SUBMITTED);
        when(request.getRequestCode()).thenReturn(code);
        when(request.getOperationType()).thenReturn(operation);
        when(operation.getDirection()).thenReturn(direction);
        when(request.getDetails()).thenReturn(details);
        when(source.getId()).thenReturn(10L);
        when(request.getSourceWarehouse()).thenReturn(source);
        return request;
    }

    private WarehouseRequestDetail detail(Long id, Long materialId, long quantity) {
        WarehouseRequestDetail detail = mock(WarehouseRequestDetail.class);
        Material material = mock(Material.class);
        when(detail.getId()).thenReturn(id);
        when(detail.getMaterial()).thenReturn(material);
        when(material.getId()).thenReturn(materialId);
        when(detail.getCondition()).thenReturn(MaterialCondition.NEW);
        when(detail.getQuantity()).thenReturn(quantity);
        return detail;
    }

    private InventoryLot lot(long onHand, long reserved, String price) {
        InventoryLot lot = mock(InventoryLot.class);
        when(lot.getOnHandQuantity()).thenReturn(onHand);
        when(lot.getReservedQuantity()).thenReturn(reserved);
        when(lot.getUnitPrice()).thenReturn(new BigDecimal(price));
        return lot;
    }
}
