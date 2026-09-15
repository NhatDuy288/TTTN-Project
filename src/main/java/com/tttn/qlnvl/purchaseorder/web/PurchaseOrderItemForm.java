package com.tttn.qlnvl.purchaseorder.web;

import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import java.math.BigDecimal;

public class PurchaseOrderItemForm {
    private Long materialId;
    private Long orderedQuantity;
    private BigDecimal unitPrice;

    public static PurchaseOrderItemForm from(PurchaseOrderItem item) {
        PurchaseOrderItemForm form = new PurchaseOrderItemForm();
        form.materialId = item.getMaterial().getId();
        form.orderedQuantity = item.getOrderedQuantity();
        form.unitPrice = item.getUnitPrice();
        return form;
    }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getOrderedQuantity() { return orderedQuantity; }
    public void setOrderedQuantity(Long orderedQuantity) { this.orderedQuantity = orderedQuantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
