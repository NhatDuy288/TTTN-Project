package com.tttn.qlnvl.warehouserequest.domain;

public enum WarehouseRequestStatus {
    DRAFT("Lưu nháp"), SUBMITTED("Chuyển duyệt"), APPROVED("Đã duyệt"),
    PROCESSING("Đang xử lý"), COMPLETED("Hoàn tất"), REJECTED("Từ chối"),
    CANCELLED("Đã hủy");

    private final String displayName;

    WarehouseRequestStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
