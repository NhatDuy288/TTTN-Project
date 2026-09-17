package com.tttn.qlnvl.warehousetransfer.domain;

public enum WarehouseTransferStatus {
    DRAFT("Lưu nháp"),
    SUBMITTED("Chờ duyệt"),
    READY_TO_TRANSFER("Sẵn sàng điều chuyển"),
    IN_TRANSIT("Đang vận chuyển"),
    COMPLETED("Hoàn tất"),
    REJECTED("Đã từ chối");

    private final String displayName;

    WarehouseTransferStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
