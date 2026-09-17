package com.tttn.qlnvl.warehousetransaction.application;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.shared.audit.AggregateType;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.shared.audit.WorkflowAction;
import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionApprovalService {
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(20, 50, 100);

    private final WarehouseTransactionRepository transactionRepository;
    private final WarehouseRequestRepository requestRepository;
    private final InventoryLotRepository lotRepository;
    private final StatusHistoryRepository historyRepository;
    private final AppUserRepository userRepository;

    public TransactionApprovalService(WarehouseTransactionRepository transactionRepository,
            WarehouseRequestRepository requestRepository, InventoryLotRepository lotRepository,
            StatusHistoryRepository historyRepository, AppUserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.requestRepository = requestRepository;
        this.lotRepository = lotRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<WarehouseTransaction> queue(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 20;
        return transactionRepository.findQueue(WarehouseTransactionStatus.SUBMITTED,
                PageRequest.of(safePage, safeSize,
                        Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
    }

    @Transactional(readOnly = true)
    public WarehouseTransaction getDetail(Long id) {
        WarehouseTransaction transaction = transactionRepository.findDetailedById(id)
                .orElseThrow(WarehouseTransactionNotFoundException::new);
        transaction.getDetails().forEach(detail -> detail.getAllocations().forEach(allocation ->
                allocation.getInventoryLot().getReceivedAt()));
        return transaction;
    }

    @Transactional
    public WarehouseTransaction approve(Long id, Long actorId, String submittedComment) {
        WarehouseTransaction transaction = lockedSubmitted(id);
        String comment = normalizeComment(submittedComment);
        AppUser actor = actor(actorId);

        transaction.approve();
        transactionRepository.saveAndFlush(transaction);
        historyRepository.save(new StatusHistory(AggregateType.TRANSACTION, transaction.getId(),
                WarehouseTransactionStatus.SUBMITTED.name(),
                WarehouseTransactionStatus.READY_FOR_CONFIRMATION.name(),
                WorkflowAction.APPROVE, actor, comment));
        return transaction;
    }

    @Transactional
    public WarehouseTransaction reject(Long id, Long actorId, String submittedComment) {
        WarehouseTransaction transaction = lockedSubmitted(id);
        WarehouseRequest request = requestRepository.findByIdForUpdate(
                        transaction.getRequest().getId())
                .orElseThrow(WarehouseTransactionNotFoundException::new);
        if (request.getStatus() != WarehouseRequestStatus.PROCESSING) {
            throw new WarehouseTransactionConflictException(
                    "Phiếu đề nghị liên quan không còn ở trạng thái đang xử lý.");
        }
        String comment = normalizeComment(submittedComment);
        AppUser actor = actor(actorId);

        if (request.getOperationType().getDirection() == OperationDirection.EXPORT) {
            releaseIssueReservations(transaction);
        }
        transaction.reject();
        request.cancel();
        transactionRepository.saveAndFlush(transaction);
        requestRepository.saveAndFlush(request);
        historyRepository.save(new StatusHistory(AggregateType.TRANSACTION, transaction.getId(),
                WarehouseTransactionStatus.SUBMITTED.name(),
                WarehouseTransactionStatus.REJECTED.name(), WorkflowAction.REJECT, actor, comment));
        historyRepository.save(new StatusHistory(AggregateType.REQUEST, request.getId(),
                WarehouseRequestStatus.PROCESSING.name(), WarehouseRequestStatus.CANCELLED.name(),
                WorkflowAction.CANCEL, actor, comment));
        return transaction;
    }

    private void releaseIssueReservations(WarehouseTransaction transaction) {
        Map<Long, Long> quantitiesByLot = new TreeMap<>();
        transaction.getDetails().forEach(detail -> detail.getAllocations().forEach(allocation ->
                quantitiesByLot.merge(allocation.getInventoryLot().getId(),
                        allocation.getAllocatedQuantity(), Long::sum)));
        if (quantitiesByLot.isEmpty()) {
            throw new WarehouseTransactionConflictException(
                    "Phân bổ FIFO của chứng từ xuất không còn nhất quán.");
        }
        List<InventoryLot> lots = lotRepository.findAllByIdForUpdate(
                new ArrayList<>(quantitiesByLot.keySet()));
        Map<Long, InventoryLot> lotsById = new LinkedHashMap<>();
        lots.forEach(lot -> lotsById.put(lot.getId(), lot));
        if (lotsById.size() != quantitiesByLot.size()) {
            throw new WarehouseTransactionConflictException(
                    "Phân bổ FIFO của chứng từ xuất không còn nhất quán.");
        }
        quantitiesByLot.forEach((lotId, quantity) -> {
            try {
                lotsById.get(lotId).releaseReservation(quantity);
            } catch (IllegalArgumentException exception) {
                throw new WarehouseTransactionConflictException(
                        "Số lượng giữ chỗ theo lô không còn nhất quán.");
            }
        });
        lotRepository.flush();
    }

    private WarehouseTransaction lockedSubmitted(Long id) {
        WarehouseTransaction transaction = transactionRepository.findByIdForUpdate(id)
                .orElseThrow(WarehouseTransactionNotFoundException::new);
        if (transaction.getStatus() != WarehouseTransactionStatus.SUBMITTED) {
            throw new WarehouseTransactionConflictException(
                    "Chỉ được duyệt chứng từ đang chờ duyệt.");
        }
        return transaction;
    }

    private AppUser actor(Long actorId) {
        return userRepository.findById(actorId)
                .orElseThrow(WarehouseTransactionNotFoundException::new);
    }

    private String normalizeComment(String value) {
        String comment = value == null || value.isBlank() ? null : value.trim();
        if (comment != null && comment.length() > 500) {
            throw new InvalidWarehouseTransactionException("comment",
                    "Ý kiến duyệt tối đa 500 ký tự.");
        }
        return comment;
    }
}
