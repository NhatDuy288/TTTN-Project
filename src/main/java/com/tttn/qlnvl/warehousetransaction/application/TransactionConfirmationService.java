package com.tttn.qlnvl.warehousetransaction.application;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderStatus;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderItemRepository;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderRepository;
import com.tttn.qlnvl.shared.audit.AggregateType;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.shared.audit.WorkflowAction;
import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionDetail;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
public class TransactionConfirmationService {
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(20, 50, 100);

    private final WarehouseTransactionRepository transactionRepository;
    private final WarehouseRequestRepository requestRepository;
    private final InventoryLotRepository lotRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final StatusHistoryRepository historyRepository;
    private final AppUserRepository userRepository;

    public TransactionConfirmationService(WarehouseTransactionRepository transactionRepository,
            WarehouseRequestRepository requestRepository, InventoryLotRepository lotRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            StatusHistoryRepository historyRepository, AppUserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.requestRepository = requestRepository;
        this.lotRepository = lotRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<WarehouseTransaction> queue(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 20;
        return transactionRepository.findQueue(WarehouseTransactionStatus.READY_FOR_CONFIRMATION,
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
    public WarehouseTransaction confirm(Long id, Long actorId) {
        WarehouseTransaction transaction = transactionRepository.findByIdForUpdate(id)
                .orElseThrow(WarehouseTransactionNotFoundException::new);
        if (transaction.getStatus() != WarehouseTransactionStatus.READY_FOR_CONFIRMATION) {
            throw new WarehouseTransactionConflictException(
                    "Chỉ được xác nhận chứng từ đang chờ xác nhận kho.");
        }
        WarehouseRequest request = requestRepository.findByIdForUpdate(
                        transaction.getRequest().getId())
                .orElseThrow(WarehouseTransactionNotFoundException::new);
        if (request.getStatus() != WarehouseRequestStatus.PROCESSING) {
            throw new WarehouseTransactionConflictException(
                    "Phiếu đề nghị liên quan không còn ở trạng thái đang xử lý.");
        }
        AppUser actor = userRepository.findById(actorId)
                .orElseThrow(WarehouseTransactionNotFoundException::new);
        Instant confirmedAt = Instant.now();
        OperationDirection direction = request.getOperationType().getDirection();

        PurchaseOrder purchaseOrder = null;
        if (direction == OperationDirection.IMPORT) {
            purchaseOrder = confirmImport(transaction, request, confirmedAt);
        } else if (direction == OperationDirection.EXPORT) {
            confirmExport(transaction);
        } else {
            throw new WarehouseTransactionConflictException(
                    "UC-14 không xử lý chứng từ điều chuyển kho.");
        }

        transaction.confirmPhysical(actor, confirmedAt,
                request.getOperationType().isRequiresAccounting());
        request.complete();
        transactionRepository.saveAndFlush(transaction);
        requestRepository.saveAndFlush(request);

        if (purchaseOrder != null) updatePurchaseOrder(purchaseOrder, confirmedAt);

        historyRepository.save(new StatusHistory(AggregateType.TRANSACTION, transaction.getId(),
                WarehouseTransactionStatus.READY_FOR_CONFIRMATION.name(),
                WarehouseTransactionStatus.COMPLETED.name(), WorkflowAction.CONFIRM_PHYSICAL,
                actor, null));
        historyRepository.save(new StatusHistory(AggregateType.REQUEST, request.getId(),
                WarehouseRequestStatus.PROCESSING.name(), WarehouseRequestStatus.COMPLETED.name(),
                WorkflowAction.COMPLETE, actor, null));
        return transaction;
    }

    private PurchaseOrder confirmImport(WarehouseTransaction transaction,
            WarehouseRequest request, Instant confirmedAt) {
        if (request.getDestinationWarehouse() == null) {
            throw new WarehouseTransactionConflictException(
                    "Chứng từ nhập không có kho nhận hợp lệ.");
        }
        PurchaseOrder purchaseOrder = lockAndValidatePurchaseOrder(request, transaction.getDetails());
        List<InventoryLot> receipts = transaction.getDetails().stream()
                .map(detail -> receiptLot(request, detail, confirmedAt))
                .toList();
        lotRepository.saveAllAndFlush(receipts);
        return purchaseOrder;
    }

    private PurchaseOrder lockAndValidatePurchaseOrder(WarehouseRequest request,
            List<WarehouseTransactionDetail> details) {
        if (request.getPurchaseOrder() == null) {
            if (details.stream().anyMatch(detail ->
                    detail.getRequestDetail().getPurchaseOrderItem() != null)) {
                throw new WarehouseTransactionConflictException(
                        "Dữ liệu Purchase Order của chứng từ nhập không nhất quán.");
            }
            return null;
        }
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByIdForUpdate(
                        request.getPurchaseOrder().getId())
                .orElseThrow(WarehouseTransactionNotFoundException::new);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.OPEN
                && purchaseOrder.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new WarehouseTransactionConflictException(
                    "Purchase Order không còn ở trạng thái có thể nhận hàng.");
        }
        Map<Long, Long> quantitiesByItem = new TreeMap<>();
        for (WarehouseTransactionDetail detail : details) {
            PurchaseOrderItem item = detail.getRequestDetail().getPurchaseOrderItem();
            if (item == null || !purchaseOrder.getId().equals(item.getPurchaseOrder().getId())) {
                throw new WarehouseTransactionConflictException(
                        "Dữ liệu Purchase Order của chứng từ nhập không nhất quán.");
            }
            quantitiesByItem.merge(item.getId(), detail.getRequestDetail().getQuantity(), Long::sum);
        }
        List<PurchaseOrderItem> lockedItems = purchaseOrderItemRepository.findAllByIdForUpdate(
                new ArrayList<>(quantitiesByItem.keySet()));
        Map<Long, PurchaseOrderItem> itemsById = new HashMap<>();
        lockedItems.forEach(item -> itemsById.put(item.getId(), item));
        if (itemsById.size() != quantitiesByItem.size()) {
            throw new WarehouseTransactionConflictException(
                    "Dữ liệu Purchase Order của chứng từ nhập không nhất quán.");
        }
        quantitiesByItem.forEach((itemId, quantity) -> {
            PurchaseOrderItem item = itemsById.get(itemId);
            long received = purchaseOrderItemRepository.receivedQuantity(itemId);
            if (quantity > item.getOrderedQuantity() - received) {
                throw new WarehouseTransactionConflictException(
                        "Số lượng nhận sẽ vượt quá số lượng đặt hàng.");
            }
        });
        return purchaseOrder;
    }

    private InventoryLot receiptLot(WarehouseRequest request,
            WarehouseTransactionDetail detail, Instant confirmedAt) {
        PurchaseOrderItem item = detail.getRequestDetail().getPurchaseOrderItem();
        return new InventoryLot(request.getDestinationWarehouse(),
                detail.getRequestDetail().getMaterial(), detail.getRequestDetail().getCondition(),
                item, detail, item == null ? null : item.getUnitPrice(),
                detail.getRequestDetail().getQuantity(), confirmedAt);
    }

    private void confirmExport(WarehouseTransaction transaction) {
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
                lotsById.get(lotId).issue(quantity);
            } catch (IllegalArgumentException exception) {
                throw new WarehouseTransactionConflictException(
                        "Tồn giữ chỗ theo lô không còn nhất quán.");
            }
        });
        lotRepository.flush();
    }

    private void updatePurchaseOrder(PurchaseOrder purchaseOrder, Instant confirmedAt) {
        boolean fullyReceived = purchaseOrder.getItems().stream().allMatch(item ->
                purchaseOrderItemRepository.receivedQuantity(item.getId())
                        >= item.getOrderedQuantity());
        purchaseOrder.registerReceipt(confirmedAt, fullyReceived);
        purchaseOrderRepository.saveAndFlush(purchaseOrder);
    }
}
