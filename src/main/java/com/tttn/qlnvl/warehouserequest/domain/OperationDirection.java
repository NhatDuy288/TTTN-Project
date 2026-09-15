package com.tttn.qlnvl.warehouserequest.domain;

public enum OperationDirection {
    IMPORT("Nhập kho"),
    EXPORT("Xuất kho"),
    TRANSFER("Điều chuyển");

    private final String displayName;

    OperationDirection(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
