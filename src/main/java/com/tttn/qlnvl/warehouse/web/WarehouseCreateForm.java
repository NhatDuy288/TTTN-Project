package com.tttn.qlnvl.warehouse.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class WarehouseCreateForm {
    @NotBlank(message = "Mã kho là bắt buộc.")
    @Size(max = 30, message = "Mã kho tối đa 30 ký tự.")
    @Pattern(regexp = "[A-Za-z0-9_]+", message = "Mã kho chỉ gồm chữ, số và dấu '_'.")
    private String warehouseCode;
    @NotBlank(message = "Tên kho là bắt buộc.")
    @Size(min = 5, max = 50, message = "Tên kho phải từ 5 đến 50 ký tự.")
    private String warehouseName;
    @NotBlank(message = "Địa chỉ là bắt buộc.")
    @Size(min = 20, max = 200, message = "Địa chỉ phải từ 20 đến 200 ký tự.")
    private String address;
    @Size(max = 200, message = "Ghi chú tối đa 200 ký tự.")
    private String note;

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
