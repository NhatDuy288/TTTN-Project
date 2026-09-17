package com.tttn.qlnvl.warehousetransfer.application;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.shared.audit.AggregateType;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.shared.audit.WorkflowAction;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransfer;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
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
public class TransferApprovalService {
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(20, 50, 100);

    private final WarehouseTransferRepository transferRepository;
    private final WarehouseRequestRepository requestRepository;
    private final InventoryLotRepository lotRepository;
    private final StatusHistoryRepository historyRepository;
    private final AppUserRepository userRepository;

    public TransferApprovalService(WarehouseTransferRepository transferRepository,
            WarehouseRequestRepository requestRepository, InventoryLotRepository lotRepository,
            StatusHistoryRepository historyRepository, AppUserRepository userRepository) {
        this.transferRepository = transferRepository;
        this.requestRepository = requestRepository;
        this.lotRepository = lotRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<WarehouseTransfer> queue(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 20;
        return transferRepository.findQueue(WarehouseTransferStatus.SUBMITTED,
                PageRequest.of(safePage, safeSize,
                        Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
    }

    @Transactional(readOnly = true)
    public WarehouseTransfer getDetail(Long id) {
        WarehouseTransfer transfer = transferRepository.findDetailedById(id)
                .orElseThrow(WarehouseTransferNotFoundException::new);
        transfer.getDetails().forEach(detail -> detail.getAllocations().forEach(allocation ->
                allocation.getInventoryLot().getReceivedAt()));
        return transfer;
    }

    @Transactional
    public WarehouseTransfer approve(Long id, Long actorId, String submittedComment) {
        WarehouseTransfer transfer = lockedSubmitted(id);
        lockedProcessingRequest(transfer);
        String comment = normalizeComment(submittedComment);
        AppUser actor = actor(actorId);

        transfer.approve();
        transferRepository.saveAndFlush(transfer);
        historyRepository.save(new StatusHistory(AggregateType.TRANSFER, transfer.getId(),
                WarehouseTransferStatus.SUBMITTED.name(),
                WarehouseTransferStatus.READY_TO_TRANSFER.name(), WorkflowAction.APPROVE,
                actor, comment));
        return transfer;
    }

    @Transactional
    public WarehouseTransfer reject(Long id, Long actorId, String submittedComment) {
        WarehouseTransfer transfer = lockedSubmitted(id);
        WarehouseRequest request = lockedProcessingRequest(transfer);
        String comment = normalizeComment(submittedComment);
        AppUser actor = actor(actorId);

        releaseTransferReservations(transfer);
        transfer.reject();
        request.cancel();
        transferRepository.saveAndFlush(transfer);
        requestRepository.saveAndFlush(request);
        historyRepository.save(new StatusHistory(AggregateType.TRANSFER, transfer.getId(),
                WarehouseTransferStatus.SUBMITTED.name(), WarehouseTransferStatus.REJECTED.name(),
                WorkflowAction.REJECT, actor, comment));
        historyRepository.save(new StatusHistory(AggregateType.REQUEST, request.getId(),
                WarehouseRequestStatus.PROCESSING.name(), WarehouseRequestStatus.CANCELLED.name(),
                WorkflowAction.CANCEL, actor, comment));
        return transfer;
    }

    private WarehouseTransfer lockedSubmitted(Long id) {
        WarehouseTransfer transfer = transferRepository.findByIdForUpdate(id)
                .orElseThrow(WarehouseTransferNotFoundException::new);
        if (transfer.getStatus() != WarehouseTransferStatus.SUBMITTED) {
            throw new WarehouseTransferConflictException(
                    "Chỉ được duyệt chứng từ điều chuyển đang chờ duyệt.");
        }
        return transfer;
    }

    private WarehouseRequest lockedProcessingRequest(WarehouseTransfer transfer) {
        WarehouseRequest request = requestRepository.findByIdForUpdate(transfer.getRequest().getId())
                .orElseThrow(WarehouseTransferNotFoundException::new);
        if (request.getStatus() != WarehouseRequestStatus.PROCESSING) {
            throw new WarehouseTransferConflictException(
                    "Phiếu đề nghị liên quan không còn ở trạng thái đang xử lý.");
        }
        return request;
    }

    private void releaseTransferReservations(WarehouseTransfer transfer) {
        Map<Long, Long> quantitiesByLot = new TreeMap<>();
        transfer.getDetails().forEach(detail -> detail.getAllocations().forEach(allocation ->
                quantitiesByLot.merge(allocation.getInventoryLot().getId(),
                        allocation.getAllocatedQuantity(), Long::sum)));
        if (quantitiesByLot.isEmpty()) {
            throw new WarehouseTransferConflictException(
                    "Phân bổ FIFO của chứng từ điều chuyển không còn nhất quán.");
        }
        List<InventoryLot> lots = lotRepository.findAllByIdForUpdate(
                new ArrayList<>(quantitiesByLot.keySet()));
        Map<Long, InventoryLot> lotsById = new LinkedHashMap<>();
        lots.forEach(lot -> lotsById.put(lot.getId(), lot));
        if (lotsById.size() != quantitiesByLot.size()) {
            throw new WarehouseTransferConflictException(
                    "Phân bổ FIFO của chứng từ điều chuyển không còn nhất quán.");
        }
        quantitiesByLot.forEach((lotId, quantity) -> {
            try {
                lotsById.get(lotId).releaseReservation(quantity);
            } catch (IllegalArgumentException exception) {
                throw new WarehouseTransferConflictException(
                        "Số lượng giữ chỗ theo lô không còn nhất quán.");
            }
        });
        lotRepository.flush();
    }

    private AppUser actor(Long actorId) {
        return userRepository.findById(actorId)
                .orElseThrow(WarehouseTransferNotFoundException::new);
    }

    private String normalizeComment(String value) {
        String comment = value == null || value.isBlank() ? null : value.trim();
        if (comment != null && comment.length() > 500) {
            throw new InvalidWarehouseTransferException("comment",
                    "Ý kiến duyệt tối đa 500 ký tự.");
        }
        return comment;
    }
}
