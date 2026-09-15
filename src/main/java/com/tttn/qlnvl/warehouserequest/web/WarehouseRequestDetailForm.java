package com.tttn.qlnvl.warehouserequest.web;

import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestDetail;

public class WarehouseRequestDetailForm {
    private Long materialGroupId;
    private Long materialId;
    private MaterialCondition condition;
    private Long quantity;
    private Long purchaseOrderItemId;

    static WarehouseRequestDetailForm from(WarehouseRequestDetail detail) {
        WarehouseRequestDetailForm form = new WarehouseRequestDetailForm();
        form.materialGroupId = detail.getMaterial().getMaterialGroup().getId();
        form.materialId = detail.getMaterial().getId();
        form.condition = detail.getCondition();
        form.quantity = detail.getQuantity();
        form.purchaseOrderItemId = detail.getPurchaseOrderItem() == null
                ? null : detail.getPurchaseOrderItem().getId();
        return form;
    }

    public Long getMaterialGroupId() { return materialGroupId; }
    public void setMaterialGroupId(Long materialGroupId) { this.materialGroupId = materialGroupId; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public MaterialCondition getCondition() { return condition; }
    public void setCondition(MaterialCondition condition) { this.condition = condition; }
    public Long getQuantity() { return quantity; }
    public void setQuantity(Long quantity) { this.quantity = quantity; }
    public Long getPurchaseOrderItemId() { return purchaseOrderItemId; }
    public void setPurchaseOrderItemId(Long purchaseOrderItemId) { this.purchaseOrderItemId = purchaseOrderItemId; }
}
