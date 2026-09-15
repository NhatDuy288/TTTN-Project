package com.tttn.qlnvl.warehouserequest.application;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialStatus;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderRepository;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.domain.WarehouseStatus;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import com.tttn.qlnvl.warehouserequest.domain.Reason;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest.DetailDefinition;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.OperationTypeRepository;
import com.tttn.qlnvl.warehouserequest.repository.ReasonRepository;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseRequestService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WarehouseRequestRepository requestRepository;
    private final OperationTypeRepository operationTypeRepository;
    private final ReasonRepository reasonRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final AppUserRepository appUserRepository;

    public WarehouseRequestService(WarehouseRequestRepository requestRepository,
            OperationTypeRepository operationTypeRepository, ReasonRepository reasonRepository,
            WarehouseRepository warehouseRepository, MaterialRepository materialRepository,
            PurchaseOrderRepository purchaseOrderRepository, AppUserRepository appUserRepository) {
        this.requestRepository = requestRepository;
        this.operationTypeRepository = operationTypeRepository;
        this.reasonRepository = reasonRepository;
        this.warehouseRepository = warehouseRepository;
        this.materialRepository = materialRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public WarehouseRequest getOwned(Long id, Long actorId) {
        WarehouseRequest request = requestRepository.findDetailedById(id)
                .orElseThrow(WarehouseRequestNotFoundException::new);
        requireOwner(request, actorId);
        return request;
    }

    @Transactional(readOnly = true)
    public WarehouseRequest getOwnedDraft(Long id, Long actorId) {
        WarehouseRequest request = getOwned(id, actorId);
        if (request.getStatus() != WarehouseRequestStatus.DRAFT) {
            throw new WarehouseRequestConflictException("Chỉ được sửa phiếu đề nghị đang lưu nháp.");
        }
        return request;
    }

    @Transactional
    public WarehouseRequest createDraft(WarehouseRequestDraftCommand command, Long actorId) {
        requireCommand(command);
        OperationType operation = activeOperation(command.operationTypeId());
        ValidatedDraft draft = validateDraft(command, operation, null);
        AppUser creator = appUserRepository.findById(actorId)
                .orElseThrow(WarehouseRequestNotFoundException::new);
        String requestCode = nextCode(operation.getDirection());
        WarehouseRequest request = new WarehouseRequest(requestCode, operation, draft.reason(),
                draft.source(), draft.destination(), draft.purchaseOrder(), draft.note(), creator);
        request.replaceDetails(draft.details());
        return requestRepository.saveAndFlush(request);
    }

    @Transactional
    public WarehouseRequest updateDraft(Long id, WarehouseRequestDraftCommand command, Long actorId) {
        requireCommand(command);
        WarehouseRequest request = requestRepository.findByIdForUpdate(id)
                .orElseThrow(WarehouseRequestNotFoundException::new);
        requireOwner(request, actorId);
        if (request.getStatus() != WarehouseRequestStatus.DRAFT) {
            throw new WarehouseRequestConflictException("Chỉ được sửa phiếu đề nghị đang lưu nháp.");
        }
        if (!request.getOperationType().isActive()) {
            throw new WarehouseRequestConflictException("Loại phiếu đã ngừng hoạt động nên không thể chỉnh sửa.");
        }
        if (!Objects.equals(request.getOperationType().getId(), command.operationTypeId())) {
            throw new InvalidWarehouseRequestException("operationTypeId",
                    "Không được đổi loại phiếu sau khi tạo.");
        }
        Long existingPoId = request.getPurchaseOrder() == null ? null : request.getPurchaseOrder().getId();
        if (!Objects.equals(existingPoId, command.purchaseOrderId())) {
            throw new InvalidWarehouseRequestException("purchaseOrderId",
                    "Không được đổi PO tham chiếu sau khi tạo.");
        }
        ValidatedDraft draft = validateDraft(command, request.getOperationType(), request.getPurchaseOrder());
        request.updateDraft(draft.reason(), draft.source(), draft.destination(), draft.note());
        request.clearDetails();
        requestRepository.flush();
        request.addDetails(draft.details());
        return requestRepository.saveAndFlush(request);
    }

    private ValidatedDraft validateDraft(WarehouseRequestDraftCommand command,
            OperationType operation, PurchaseOrder immutablePurchaseOrder) {
        Reason reason = reasonRepository.findByIdAndActiveTrue(requiredId(command.reasonId(), "reasonId",
                        "Lý do là bắt buộc."))
                .orElseThrow(() -> invalid("reasonId", "Lý do không tồn tại hoặc không hoạt động."));
        if (reason.getDirection() != operation.getDirection()) {
            throw invalid("reasonId", "Lý do không phù hợp với loại phiếu.");
        }
        String note = normalize(command.note());
        if (note != null && note.length() > 300) {
            throw invalid("note", "Ghi chú tối đa 300 ký tự.");
        }
        if (reason.isRequiresNote() && note == null) {
            throw invalid("note", "Ghi chú là bắt buộc với lý do đã chọn.");
        }

        Warehouse source = null;
        Warehouse destination = null;
        if (operation.getDirection() == OperationDirection.IMPORT) {
            destination = activeWarehouse(command.destinationWarehouseId(), "destinationWarehouseId");
            if (command.sourceWarehouseId() != null) throw invalid("sourceWarehouseId", "Phiếu nhập không dùng kho nguồn.");
        } else if (operation.getDirection() == OperationDirection.EXPORT) {
            source = activeWarehouse(command.sourceWarehouseId(), "sourceWarehouseId");
            if (command.destinationWarehouseId() != null) throw invalid("destinationWarehouseId", "Phiếu xuất không dùng kho nhận.");
        } else {
            source = activeWarehouse(command.sourceWarehouseId(), "sourceWarehouseId");
            destination = activeWarehouse(command.destinationWarehouseId(), "destinationWarehouseId");
            if (source.getId().equals(destination.getId())) {
                throw invalid("destinationWarehouseId", "Kho nguồn và kho nhận phải khác nhau.");
            }
        }

        PurchaseOrder purchaseOrder = immutablePurchaseOrder;
        if (immutablePurchaseOrder == null && command.purchaseOrderId() != null) {
            purchaseOrder = purchaseOrderRepository.findDetailedById(command.purchaseOrderId())
                    .orElseThrow(() -> invalid("purchaseOrderId", "PO không tồn tại."));
        }
        if (operation.isRequiresPo() && purchaseOrder == null) {
            throw invalid("purchaseOrderId", "Loại phiếu này bắt buộc chọn PO.");
        }
        if (operation.getDirection() != OperationDirection.IMPORT && purchaseOrder != null) {
            throw invalid("purchaseOrderId", "Chỉ phiếu nhập được tham chiếu PO.");
        }
        List<DetailDefinition> details = validateDetails(command.details(), operation, purchaseOrder);
        return new ValidatedDraft(reason, source, destination, purchaseOrder, note, details);
    }

    private List<DetailDefinition> validateDetails(List<WarehouseRequestDraftCommand.Detail> submitted,
            OperationType operation, PurchaseOrder purchaseOrder) {
        List<WarehouseRequestDraftCommand.Detail> rows = submitted == null ? List.of() : submitted;
        List<IndexedDetail> details = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index) != null && !rows.get(index).isBlank()) details.add(new IndexedDetail(index, rows.get(index)));
        }
        List<Long> materialIds = details.stream().map(d -> d.detail().materialId())
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Material> materials = new HashMap<>();
        if (!materialIds.isEmpty()) {
            materialRepository.findDetailedByIdIn(materialIds).forEach(m -> materials.put(m.getId(), m));
        }
        Set<Long> allowedGroupIds = operation.getMaterialGroups().stream()
                .map(group -> group.getId()).collect(Collectors.toSet());
        Map<Long, PurchaseOrderItem> poItems = purchaseOrder == null ? Map.of()
                : purchaseOrder.getItems().stream().collect(Collectors.toMap(PurchaseOrderItem::getId, item -> item));
        List<DetailDefinition> result = new ArrayList<>();
        for (IndexedDetail indexed : details) {
            int index = indexed.index(); WarehouseRequestDraftCommand.Detail row = indexed.detail();
            Material material = materials.get(row.materialId());
            if (material == null || material.getStatus() != MaterialStatus.ACTIVE) {
                throw invalidDetail(index, "materialId", "Vật tư không tồn tại hoặc không hoạt động.");
            }
            if (!allowedGroupIds.contains(material.getMaterialGroup().getId())) {
                throw invalidDetail(index, "materialId", "Nhóm vật tư không được phép với loại phiếu.");
            }
            MaterialCondition condition = row.condition();
            if (condition == null || !operation.getAllowedConditions().contains(condition)) {
                throw invalidDetail(index, "condition", "Tình trạng vật tư không được phép với loại phiếu.");
            }
            if (row.quantity() == null || row.quantity() <= 0) {
                throw invalidDetail(index, "quantity", "Số lượng phải lớn hơn 0.");
            }
            PurchaseOrderItem poItem = null;
            if (purchaseOrder != null) {
                poItem = poItems.get(row.purchaseOrderItemId());
                if (poItem == null || !poItem.getMaterial().getId().equals(material.getId())) {
                    throw invalidDetail(index, "purchaseOrderItemId", "Dòng PO không phù hợp với vật tư.");
                }
            } else if (row.purchaseOrderItemId() != null) {
                throw invalidDetail(index, "purchaseOrderItemId", "Không được chọn dòng PO khi phiếu không tham chiếu PO.");
            }
            result.add(new DetailDefinition(material, condition, row.quantity(), poItem));
        }
        return result;
    }

    private String nextCode(OperationDirection direction) {
        requestRepository.lockForCodeGeneration();
        String prefix = switch (direction) { case IMPORT -> "INP"; case EXPORT -> "OUT"; case TRANSFER -> "TRF"; };
        String prefixDate = prefix + "_" + LocalDate.now(BUSINESS_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE);
        int sequence = requestRepository.maxSequenceFor(prefixDate) + 1;
        if (sequence > 999) throw new WarehouseRequestConflictException("Đã hết dải mã phiếu trong ngày.");
        return prefixDate + "_" + String.format("%03d", sequence);
    }

    private OperationType activeOperation(Long id) {
        return operationTypeRepository.findDetailedByIdAndActiveTrue(requiredId(id, "operationTypeId", "Loại phiếu là bắt buộc."))
                .orElseThrow(() -> invalid("operationTypeId", "Loại phiếu không tồn tại hoặc không hoạt động."));
    }
    private Warehouse activeWarehouse(Long id, String field) {
        Warehouse warehouse = warehouseRepository.findById(requiredId(id, field, "Kho là bắt buộc."))
                .orElseThrow(() -> invalid(field, "Kho không tồn tại."));
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) throw invalid(field, "Kho không hoạt động.");
        return warehouse;
    }
    private void requireOwner(WarehouseRequest request, Long actorId) {
        if (actorId == null || !actorId.equals(request.getCreatedBy().getId())) throw new WarehouseRequestNotFoundException();
    }
    private void requireCommand(WarehouseRequestDraftCommand command) {
        if (command == null) throw invalid(null, "Dữ liệu phiếu đề nghị là bắt buộc.");
    }
    private Long requiredId(Long id, String field, String message) { if (id == null) throw invalid(field, message); return id; }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private InvalidWarehouseRequestException invalid(String field, String message) { return new InvalidWarehouseRequestException(field, message); }
    private InvalidWarehouseRequestException invalidDetail(int index, String field, String message) {
        return invalid("details[" + index + "]." + field, message);
    }
    private record IndexedDetail(int index, WarehouseRequestDraftCommand.Detail detail) {}
    private record ValidatedDraft(Reason reason, Warehouse source, Warehouse destination,
            PurchaseOrder purchaseOrder, String note, List<DetailDefinition> details) {}
}
