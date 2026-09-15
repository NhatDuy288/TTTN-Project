package com.tttn.qlnvl.purchaseorder.web;

import com.tttn.qlnvl.purchaseorder.application.PurchaseOrderDraftCommand;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PurchaseOrderForm {
    private Long materialGroupId;
    @NotBlank(message = "Mã PO là bắt buộc.")
    @Size(max = 50, message = "Mã PO tối đa 50 ký tự.")
    private String poCode;
    @NotBlank(message = "Nhà cung cấp là bắt buộc.")
    @Size(max = 300, message = "Nhà cung cấp tối đa 300 ký tự.")
    private String supplierName;
    @NotNull(message = "Ngày đặt hàng là bắt buộc.")
    private LocalDate orderDate;
    private List<PurchaseOrderItemForm> items = new ArrayList<>();

    public PurchaseOrderForm() {
        items.add(new PurchaseOrderItemForm());
    }

    public static PurchaseOrderForm from(PurchaseOrder order) {
        PurchaseOrderForm form = new PurchaseOrderForm();
        form.poCode = order.getPoCode();
        form.supplierName = order.getSupplierName();
        form.orderDate = order.getOrderDate();
        form.materialGroupId = order.getItems().isEmpty()
                ? null
                : order.getItems().getFirst().getMaterial().getMaterialGroup().getId();
        form.items = order.getItems().stream().map(PurchaseOrderItemForm::from)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (form.items.isEmpty()) {
            form.items.add(new PurchaseOrderItemForm());
        }
        return form;
    }

    public PurchaseOrderDraftCommand toCommand() {
        List<PurchaseOrderDraftCommand.Item> commands = items == null ? List.of() : items.stream()
                .map(item -> new PurchaseOrderDraftCommand.Item(
                        item.getMaterialId(), item.getOrderedQuantity(), item.getUnitPrice()))
                .toList();
        return new PurchaseOrderDraftCommand(poCode, supplierName, orderDate, commands);
    }

    public String getPoCode() { return poCode; }
    public void setPoCode(String poCode) { this.poCode = poCode; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
    public Long getMaterialGroupId() { return materialGroupId; }
    public void setMaterialGroupId(Long materialGroupId) { this.materialGroupId = materialGroupId; }
    public List<PurchaseOrderItemForm> getItems() { return items; }
    public void setItems(List<PurchaseOrderItemForm> items) { this.items = items; }
}
