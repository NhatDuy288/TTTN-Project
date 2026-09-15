package com.tttn.qlnvl.warehouserequest.web;

import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestDraftCommand;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class WarehouseRequestForm {
    @NotNull(message = "Loại phiếu là bắt buộc.")
    private Long operationTypeId;
    @NotNull(message = "Lý do là bắt buộc.")
    private Long reasonId;
    private Long sourceWarehouseId;
    private Long destinationWarehouseId;
    private Long purchaseOrderId;
    @Size(max = 300, message = "Ghi chú tối đa 300 ký tự.")
    private String note;
    private List<WarehouseRequestDetailForm> details = new ArrayList<>();

    public WarehouseRequestForm() {
        details.add(new WarehouseRequestDetailForm());
    }

    static WarehouseRequestForm from(WarehouseRequest request) {
        WarehouseRequestForm form = new WarehouseRequestForm();
        form.operationTypeId = request.getOperationType().getId();
        form.reasonId = request.getReason().getId();
        form.sourceWarehouseId = request.getSourceWarehouse() == null ? null : request.getSourceWarehouse().getId();
        form.destinationWarehouseId = request.getDestinationWarehouse() == null ? null : request.getDestinationWarehouse().getId();
        form.purchaseOrderId = request.getPurchaseOrder() == null ? null : request.getPurchaseOrder().getId();
        form.note = request.getNote();
        form.details = request.getDetails().stream().map(WarehouseRequestDetailForm::from)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (form.details.isEmpty()) form.details.add(new WarehouseRequestDetailForm());
        return form;
    }

    WarehouseRequestDraftCommand toCommand() {
        List<WarehouseRequestDraftCommand.Detail> commands = details == null ? List.of() : details.stream()
                .map(detail -> new WarehouseRequestDraftCommand.Detail(detail.getMaterialId(),
                        detail.getCondition(), detail.getQuantity(), detail.getPurchaseOrderItemId()))
                .toList();
        return new WarehouseRequestDraftCommand(operationTypeId, reasonId, sourceWarehouseId,
                destinationWarehouseId, purchaseOrderId, note, commands);
    }

    public Long getOperationTypeId() { return operationTypeId; }
    public void setOperationTypeId(Long operationTypeId) { this.operationTypeId = operationTypeId; }
    public Long getReasonId() { return reasonId; }
    public void setReasonId(Long reasonId) { this.reasonId = reasonId; }
    public Long getSourceWarehouseId() { return sourceWarehouseId; }
    public void setSourceWarehouseId(Long sourceWarehouseId) { this.sourceWarehouseId = sourceWarehouseId; }
    public Long getDestinationWarehouseId() { return destinationWarehouseId; }
    public void setDestinationWarehouseId(Long destinationWarehouseId) { this.destinationWarehouseId = destinationWarehouseId; }
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<WarehouseRequestDetailForm> getDetails() { return details; }
    public void setDetails(List<WarehouseRequestDetailForm> details) { this.details = details; }
}
