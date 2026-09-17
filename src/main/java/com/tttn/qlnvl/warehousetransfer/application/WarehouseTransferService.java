package com.tttn.qlnvl.warehousetransfer.application;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.shared.audit.AggregateType;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.shared.audit.WorkflowAction;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransfer;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseTransferService {
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(20, 50, 100);

    private final WarehouseTransferRepository transferRepository;
    private final StatusHistoryRepository historyRepository;
    private final AppUserRepository userRepository;

    public WarehouseTransferService(WarehouseTransferRepository transferRepository,
            StatusHistoryRepository historyRepository, AppUserRepository userRepository) {
        this.transferRepository = transferRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<WarehouseTransfer> queue(int page, int size, WarehouseTransferStatus status) {
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 20;
        WarehouseTransferStatus safeStatus = status == null
                ? WarehouseTransferStatus.DRAFT : status;
        return transferRepository.findQueue(safeStatus,
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
    public WarehouseTransfer submit(Long id, Long actorId) {
        WarehouseTransfer transfer = transferRepository.findByIdForUpdate(id)
                .orElseThrow(WarehouseTransferNotFoundException::new);
        if (transfer.getStatus() != WarehouseTransferStatus.DRAFT) {
            throw new WarehouseTransferConflictException(
                    "Chỉ được chuyển duyệt chứng từ điều chuyển đang lưu nháp.");
        }
        if (transfer.getRequest().getStatus() != WarehouseRequestStatus.PROCESSING) {
            throw new WarehouseTransferConflictException(
                    "Phiếu đề nghị liên quan không còn ở trạng thái đang xử lý.");
        }
        AppUser actor = userRepository.findById(actorId)
                .orElseThrow(WarehouseTransferNotFoundException::new);

        transfer.submit();
        transferRepository.saveAndFlush(transfer);
        historyRepository.save(new StatusHistory(AggregateType.TRANSFER, transfer.getId(),
                WarehouseTransferStatus.DRAFT.name(), WarehouseTransferStatus.SUBMITTED.name(),
                WorkflowAction.SUBMIT, actor, null));
        return transfer;
    }
}
