package com.tttn.qlnvl.warehousetransaction.application;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.shared.audit.AggregateType;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.shared.audit.WorkflowAction;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseTransactionService {
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(20, 50, 100);

    private final WarehouseTransactionRepository transactionRepository;
    private final StatusHistoryRepository historyRepository;
    private final AppUserRepository userRepository;

    public WarehouseTransactionService(WarehouseTransactionRepository transactionRepository,
            StatusHistoryRepository historyRepository, AppUserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<WarehouseTransaction> queue(int page, int size,
            WarehouseTransactionStatus status) {
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 20;
        WarehouseTransactionStatus safeStatus = status == null
                ? WarehouseTransactionStatus.DRAFT : status;
        return transactionRepository.findQueue(safeStatus,
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
    public WarehouseTransaction submit(Long id, Long actorId, String submittedExecutionNote) {
        WarehouseTransaction transaction = transactionRepository.findByIdForUpdate(id)
                .orElseThrow(WarehouseTransactionNotFoundException::new);
        if (transaction.getStatus() != WarehouseTransactionStatus.DRAFT) {
            throw new WarehouseTransactionConflictException(
                    "Chỉ được chuyển duyệt chứng từ đang lưu nháp.");
        }
        String executionNote = normalizeExecutionNote(submittedExecutionNote);
        AppUser actor = userRepository.findById(actorId)
                .orElseThrow(WarehouseTransactionNotFoundException::new);

        transaction.submit(executionNote);
        transactionRepository.saveAndFlush(transaction);
        historyRepository.save(new StatusHistory(AggregateType.TRANSACTION, transaction.getId(),
                WarehouseTransactionStatus.DRAFT.name(), WarehouseTransactionStatus.SUBMITTED.name(),
                WorkflowAction.SUBMIT, actor, null));
        return transaction;
    }

    private String normalizeExecutionNote(String value) {
        String note = value == null || value.isBlank() ? null : value.trim();
        if (note != null && note.length() > 1000) {
            throw new InvalidWarehouseTransactionException("executionNote",
                    "Ghi chú thực hiện tối đa 1000 ký tự.");
        }
        return note;
    }
}
