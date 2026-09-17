package com.tttn.qlnvl.warehousetransaction.domain;

public enum AccountingStatus {
    NOT_REQUIRED("Không yêu cầu hạch toán"),
    PENDING("Chờ hạch toán");

    private final String displayName;

    AccountingStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
