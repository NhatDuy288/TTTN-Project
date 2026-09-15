package com.tttn.qlnvl.purchaseorder.domain;

public enum PurchaseOrderStatus {
    DRAFT("Lưu nháp"),
    OPEN("Đang mở"),
    PARTIALLY_RECEIVED("Đã nhận một phần"),
    COMPLETED("Hoàn tất"),
    CANCELLED("Đã hủy");

    private final String displayName;

    PurchaseOrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
