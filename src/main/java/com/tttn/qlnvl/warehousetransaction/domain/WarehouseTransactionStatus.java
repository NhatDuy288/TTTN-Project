package com.tttn.qlnvl.warehousetransaction.domain;

public enum WarehouseTransactionStatus {
    DRAFT("Lưu nháp"),
    SUBMITTED("Chờ duyệt"),
    READY_FOR_CONFIRMATION("Chờ xác nhận kho"),
    COMPLETED("Hoàn tất"),
    REJECTED("Từ chối");

    private final String displayName;

    WarehouseTransactionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
