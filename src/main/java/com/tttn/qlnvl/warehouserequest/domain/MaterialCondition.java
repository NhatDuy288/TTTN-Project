package com.tttn.qlnvl.warehouserequest.domain;

public enum MaterialCondition {
    NEW("Mới"),
    OLD("Cũ"),
    BROKEN("Hỏng"),
    TEMP("Mẫu/Tạm");

    private final String displayName;

    MaterialCondition(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
