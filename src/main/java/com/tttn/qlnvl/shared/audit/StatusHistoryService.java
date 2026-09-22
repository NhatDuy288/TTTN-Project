package com.tttn.qlnvl.shared.audit;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.auth.domain.Role;
import com.tttn.qlnvl.warehouserequest.application.RequestApprovalService;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestService;
import com.tttn.qlnvl.warehousetransaction.application.TransactionApprovalService;
import com.tttn.qlnvl.warehousetransaction.application.TransactionConfirmationService;
import com.tttn.qlnvl.warehousetransaction.application.WarehouseTransactionService;
import com.tttn.qlnvl.warehousetransfer.application.TransferApprovalService;
import com.tttn.qlnvl.warehousetransfer.application.TransferConfirmationService;
import com.tttn.qlnvl.warehousetransfer.application.WarehouseTransferService;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatusHistoryService {
    private final StatusHistoryRepository historyRepository;
    private final WarehouseRequestService requestService;
    private final RequestApprovalService requestApprovalService;
    private final WarehouseTransactionService transactionService;
    private final TransactionApprovalService transactionApprovalService;
    private final TransactionConfirmationService transactionConfirmationService;
    private final WarehouseTransferService transferService;
    private final TransferApprovalService transferApprovalService;
    private final TransferConfirmationService transferConfirmationService;

    public StatusHistoryService(StatusHistoryRepository historyRepository,
            WarehouseRequestService requestService, RequestApprovalService requestApprovalService,
            WarehouseTransactionService transactionService,
            TransactionApprovalService transactionApprovalService,
            TransactionConfirmationService transactionConfirmationService,
            WarehouseTransferService transferService,
            TransferApprovalService transferApprovalService,
            TransferConfirmationService transferConfirmationService) {
        this.historyRepository = historyRepository;
        this.requestService = requestService;
        this.requestApprovalService = requestApprovalService;
        this.transactionService = transactionService;
        this.transactionApprovalService = transactionApprovalService;
        this.transactionConfirmationService = transactionConfirmationService;
        this.transferService = transferService;
        this.transferApprovalService = transferApprovalService;
        this.transferConfirmationService = transferConfirmationService;
    }

    @Transactional(readOnly = true)
    public Timeline get(AggregateType type, Long id, AppUserPrincipal viewer) {
        String title;
        String backUrl;
        Role role = viewer.getRole();
        switch (type) {
            case REQUEST -> {
                if (role == Role.REQUESTER) {
                    title = requestService.getOwned(id, viewer.getId()).getRequestCode();
                    backUrl = "/requests/" + id;
                } else if (role == Role.REQUEST_APPROVER) {
                    title = requestApprovalService.getDetail(id).getRequestCode();
                    backUrl = "/request-approvals/" + id;
                } else {
                    throw denied();
                }
            }
            case TRANSACTION -> {
                if (role == Role.INVENTORY_STAFF) {
                    title = transactionService.getDetail(id).getTransactionCode();
                    backUrl = "/warehouse-transactions/" + id;
                } else if (role == Role.INVENTORY_APPROVER) {
                    title = transactionApprovalService.getDetail(id).getTransactionCode();
                    backUrl = "/transaction-approvals/" + id;
                } else if (role == Role.WAREHOUSE_KEEPER) {
                    title = transactionConfirmationService.getDetail(id).getTransactionCode();
                    backUrl = "/transaction-confirmations/" + id;
                } else {
                    throw denied();
                }
            }
            case TRANSFER -> {
                if (role == Role.INVENTORY_STAFF) {
                    title = transferService.getDetail(id).getRequest().getRequestCode();
                    backUrl = "/warehouse-transfers/" + id;
                } else if (role == Role.INVENTORY_APPROVER) {
                    title = transferApprovalService.getDetail(id).getRequest().getRequestCode();
                    backUrl = "/transfer-approvals/" + id;
                } else if (role == Role.WAREHOUSE_KEEPER) {
                    title = transferConfirmationService.getDetail(id).getRequest().getRequestCode();
                    backUrl = "/transfer-confirmations/source/" + id;
                } else {
                    throw denied();
                }
            }
            default -> throw new IllegalArgumentException("Unsupported aggregate type");
        }
        return new Timeline(title, backUrl,
                historyRepository.findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(type, id));
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Record access denied");
    }

    public record Timeline(String title, String backUrl, List<StatusHistory> events) {}
}
