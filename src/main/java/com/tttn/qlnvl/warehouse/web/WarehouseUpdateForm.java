package com.tttn.qlnvl.warehouse.web;

import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.domain.WarehouseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class WarehouseUpdateForm {
    @NotBlank(message = "Tên kho là bắt buộc.")
    @Size(min = 5, max = 50, message = "Tên kho phải từ 5 đến 50 ký tự.")
    private String warehouseName;
    @NotBlank(message = "Địa chỉ là bắt buộc.")
    @Size(min = 20, max = 200, message = "Địa chỉ phải từ 20 đến 200 ký tự.")
    private String address;
    @Size(max = 200, message = "Ghi chú tối đa 200 ký tự.")
    private String note;
    @NotNull(message = "Vui lòng chọn trạng thái.")
    private WarehouseStatus status;

    public static WarehouseUpdateForm from(Warehouse warehouse) {
        WarehouseUpdateForm form = new WarehouseUpdateForm();
        form.warehouseName = warehouse.getWarehouseName();
        form.address = warehouse.getAddress();
        form.note = warehouse.getNote();
        form.status = warehouse.getStatus();
        return form;
    }

    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public WarehouseStatus getStatus() { return status; }
    public void setStatus(WarehouseStatus status) { this.status = status; }
}
