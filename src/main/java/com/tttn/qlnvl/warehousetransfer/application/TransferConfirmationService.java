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
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferDetail;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import java.time.Instant;
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
public class TransferConfirmationService {
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(20, 50, 100);

    private final WarehouseTransferRepository transferRepository;
    private final WarehouseRequestRepository requestRepository;
    private final InventoryLotRepository lotRepository;
    private final StatusHistoryRepository historyRepository;
    private final AppUserRepository userRepository;

    public TransferConfirmationService(WarehouseTransferRepository transferRepository,
            WarehouseRequestRepository requestRepository, InventoryLotRepository lotRepository,
            StatusHistoryRepository historyRepository, AppUserRepository userRepository) {
        this.transferRepository = transferRepository;
        this.requestRepository = requestRepository;
        this.lotRepository = lotRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<WarehouseTransfer> sourceQueue(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 20;
        return transferRepository.findQueue(WarehouseTransferStatus.READY_TO_TRANSFER,
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
    public WarehouseTransfer confirmSource(Long id, Long actorId) {
        WarehouseTransfer transfer = transferRepository.findByIdForUpdate(id)
                .orElseThrow(WarehouseTransferNotFoundException::new);
        if (transfer.getStatus() != WarehouseTransferStatus.READY_TO_TRANSFER) {
            throw new WarehouseTransferConflictException(
                    "Chỉ được xác nhận xuất nguồn khi điều chuyển đang sẵn sàng.");
        }
        WarehouseRequest request = requestRepository.findByIdForUpdate(
                        transfer.getRequest().getId())
                .orElseThrow(WarehouseTransferNotFoundException::new);
        if (request.getStatus() != WarehouseRequestStatus.PROCESSING) {
            throw new WarehouseTransferConflictException(
                    "Phiếu đề nghị liên quan không còn ở trạng thái đang xử lý.");
        }
        AppUser actor = userRepository.findById(actorId)
                .orElseThrow(WarehouseTransferNotFoundException::new);

        issueAllocatedLots(transfer);
        transfer.confirmSource(actor, Instant.now());
        transferRepository.saveAndFlush(transfer);
        historyRepository.save(new StatusHistory(AggregateType.TRANSFER, transfer.getId(),
                WarehouseTransferStatus.READY_TO_TRANSFER.name(),
                WarehouseTransferStatus.IN_TRANSIT.name(), WorkflowAction.CONFIRM_SOURCE,
                actor, null));
        return transfer;
    }

    private void issueAllocatedLots(WarehouseTransfer transfer) {
        Map<Long, Long> quantitiesByLot = new TreeMap<>();
        if (transfer.getDetails().isEmpty()) {
            throw inconsistentAllocation();
        }
        for (WarehouseTransferDetail detail : transfer.getDetails()) {
            long allocatedQuantity = detail.getAllocations().stream()
                    .mapToLong(allocation -> allocation.getAllocatedQuantity())
                    .sum();
            if (detail.getAllocations().isEmpty()
                    || allocatedQuantity != detail.getRequestDetail().getQuantity()) {
                throw inconsistentAllocation();
            }
            detail.getAllocations().forEach(allocation ->
                    quantitiesByLot.merge(allocation.getInventoryLot().getId(),
                            allocation.getAllocatedQuantity(), Long::sum));
        }
        List<InventoryLot> lots = lotRepository.findAllByIdForUpdate(
                new ArrayList<>(quantitiesByLot.keySet()));
        Map<Long, InventoryLot> lotsById = new LinkedHashMap<>();
        lots.forEach(lot -> lotsById.put(lot.getId(), lot));
        if (lotsById.size() != quantitiesByLot.size()) {
            throw inconsistentAllocation();
        }
        quantitiesByLot.forEach((lotId, quantity) -> {
            try {
                lotsById.get(lotId).issue(quantity);
            } catch (IllegalArgumentException exception) {
                throw new WarehouseTransferConflictException(
                        "Tồn giữ chỗ theo lô điều chuyển không còn nhất quán.");
            }
        });
        lotRepository.flush();
    }

    private WarehouseTransferConflictException inconsistentAllocation() {
        return new WarehouseTransferConflictException(
                "Phân bổ FIFO của chứng từ điều chuyển không còn nhất quán.");
    }
}
