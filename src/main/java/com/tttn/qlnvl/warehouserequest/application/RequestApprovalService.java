package com.tttn.qlnvl.warehouserequest.application;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.shared.audit.AggregateType;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.shared.audit.WorkflowAction;
import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.StockReservation;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestDetail;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.StockReservationRepository;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionDetail;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransfer;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferDetail;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestApprovalService {
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(20, 50, 100);

    private final WarehouseRequestRepository requestRepository;
    private final StockReservationRepository reservationRepository;
    private final InventoryLotRepository lotRepository;
    private final WarehouseTransactionRepository transactionRepository;
    private final WarehouseTransferRepository transferRepository;
    private final StatusHistoryRepository historyRepository;
    private final AppUserRepository userRepository;

    public RequestApprovalService(WarehouseRequestRepository requestRepository,
            StockReservationRepository reservationRepository,
            InventoryLotRepository lotRepository,
            WarehouseTransactionRepository transactionRepository,
            WarehouseTransferRepository transferRepository,
            StatusHistoryRepository historyRepository,
            AppUserRepository userRepository) {
        this.requestRepository = requestRepository;
        this.reservationRepository = reservationRepository;
        this.lotRepository = lotRepository;
        this.transactionRepository = transactionRepository;
        this.transferRepository = transferRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<WarehouseRequest> queue(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 20;
        return requestRepository.findApprovalQueue(WarehouseRequestStatus.SUBMITTED,
                PageRequest.of(safePage, safeSize,
                        Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
    }

    @Transactional(readOnly = true)
    public WarehouseRequest getDetail(Long id) {
        return requestRepository.findDetailedById(id)
                .orElseThrow(WarehouseRequestNotFoundException::new);
    }

    @Transactional
    public WarehouseRequest approve(Long id, Long actorId, String submittedComment) {
        WarehouseRequest request = lockedSubmitted(id);
        AppUser actor = actor(actorId);
        String comment = normalizeComment(submittedComment);

        OperationDirection direction = request.getOperationType().getDirection();
        if (direction == OperationDirection.TRANSFER) {
            WarehouseTransfer transfer = new WarehouseTransfer(request);
            convertTransferReservations(request, transfer);
            transferRepository.saveAndFlush(transfer);
        } else {
            WarehouseTransaction transaction = new WarehouseTransaction(request);
            if (direction == OperationDirection.EXPORT) {
                convertIssueReservations(request, transaction);
            }
            transactionRepository.saveAndFlush(transaction);
        }

        request.approve();
        historyRepository.save(new StatusHistory(AggregateType.REQUEST, request.getId(),
                WarehouseRequestStatus.SUBMITTED.name(), WarehouseRequestStatus.APPROVED.name(),
                WorkflowAction.APPROVE, actor, comment));
        request.startProcessing();
        requestRepository.saveAndFlush(request);
        historyRepository.save(new StatusHistory(AggregateType.REQUEST, request.getId(),
                WarehouseRequestStatus.APPROVED.name(), WarehouseRequestStatus.PROCESSING.name(),
                WorkflowAction.START_PROCESSING, actor, null));
        return request;
    }

    @Transactional
    public WarehouseRequest reject(Long id, Long actorId, String submittedComment) {
        WarehouseRequest request = lockedSubmitted(id);
        AppUser actor = actor(actorId);
        String comment = normalizeComment(submittedComment);

        if (request.getOperationType().getDirection() != OperationDirection.IMPORT) {
            List<StockReservation> reservations = reservationRepository
                    .findActiveByRequestIdForUpdate(request.getId());
            reservations.forEach(StockReservation::release);
            reservationRepository.saveAll(reservations);
        }

        request.reject();
        requestRepository.saveAndFlush(request);
        historyRepository.save(new StatusHistory(AggregateType.REQUEST, request.getId(),
                WarehouseRequestStatus.SUBMITTED.name(), WarehouseRequestStatus.REJECTED.name(),
                WorkflowAction.REJECT, actor, comment));
        return request;
    }

    private void convertIssueReservations(WarehouseRequest request,
            WarehouseTransaction transaction) {
        Map<Long, WarehouseTransactionDetail> executionDetails = new HashMap<>();
        transaction.getDetails().forEach(detail ->
                executionDetails.put(detail.getRequestDetail().getId(), detail));
        convertReservations(request, (detail, lot, quantity) ->
                executionDetails.get(detail.getId()).allocate(lot, quantity));
    }

    private void convertTransferReservations(WarehouseRequest request, WarehouseTransfer transfer) {
        Map<Long, WarehouseTransferDetail> executionDetails = new HashMap<>();
        transfer.getDetails().forEach(detail ->
                executionDetails.put(detail.getRequestDetail().getId(), detail));
        convertReservations(request, (detail, lot, quantity) ->
                executionDetails.get(detail.getId()).allocate(lot, quantity));
    }

    private void convertReservations(WarehouseRequest request, AllocationWriter writer) {
        List<StockReservation> reservations = reservationRepository
                .findActiveByRequestIdForUpdate(request.getId());
        Map<Long, StockReservation> byDetail = new HashMap<>();
        reservations.forEach(reservation -> byDetail.put(
                reservation.getRequestDetail().getId(), reservation));

        List<WarehouseRequestDetail> details = request.getDetails().stream()
                .sorted(Comparator.comparing((WarehouseRequestDetail detail) -> detail.getMaterial().getId())
                        .thenComparing(detail -> detail.getCondition().name())
                        .thenComparing(WarehouseRequestDetail::getId))
                .toList();
        for (WarehouseRequestDetail detail : details) {
            StockReservation reservation = byDetail.get(detail.getId());
            if (reservation == null || reservation.getQuantity() != detail.getQuantity()) {
                throw new WarehouseRequestConflictException(
                        "Soft reservation của phiếu không còn nhất quán. Vui lòng tải lại dữ liệu.");
            }
            long remaining = detail.getQuantity();
            List<InventoryLot> lots = lotRepository.findAvailableFifoForUpdate(
                    request.getSourceWarehouse().getId(), detail.getMaterial().getId(),
                    detail.getCondition());
            for (InventoryLot lot : lots) {
                long allocated = Math.min(remaining,
                        lot.getOnHandQuantity() - lot.getReservedQuantity());
                if (allocated <= 0) continue;
                lot.reserve(allocated);
                writer.allocate(detail, lot, allocated);
                remaining -= allocated;
                if (remaining == 0) break;
            }
            if (remaining != 0) {
                throw new WarehouseRequestConflictException(
                        "Tồn theo FIFO không còn đủ để duyệt phiếu. Vui lòng tải lại dữ liệu.");
            }
            reservation.convert();
        }
        if (byDetail.size() != details.size()) {
            throw new WarehouseRequestConflictException(
                    "Soft reservation của phiếu không còn nhất quán. Vui lòng tải lại dữ liệu.");
        }
        lotRepository.flush();
        reservationRepository.saveAll(reservations);
    }

    private WarehouseRequest lockedSubmitted(Long id) {
        WarehouseRequest request = requestRepository.findByIdForUpdate(id)
                .orElseThrow(WarehouseRequestNotFoundException::new);
        if (request.getStatus() != WarehouseRequestStatus.SUBMITTED) {
            throw new WarehouseRequestConflictException("Chỉ được duyệt phiếu đang chờ duyệt.");
        }
        return request;
    }

    private AppUser actor(Long actorId) {
        return userRepository.findById(actorId)
                .orElseThrow(WarehouseRequestNotFoundException::new);
    }

    private String normalizeComment(String value) {
        String comment = value == null || value.isBlank() ? null : value.trim();
        if (comment != null && comment.length() > 500) {
            throw new InvalidWarehouseRequestException("comment", "Ý kiến duyệt tối đa 500 ký tự.");
        }
        return comment;
    }

    @FunctionalInterface
    private interface AllocationWriter {
        void allocate(WarehouseRequestDetail detail, InventoryLot lot, long quantity);
    }
}
