package com.tttn.qlnvl.material.domain;

public enum MaterialUnit {
    BO("Bộ"), CAI("Cái"), CUON("Cuộn"), HOP("Hộp"), KHAY("Khay"),
    MAY("Máy"), TO("Tờ"), THUNG("Thùng"), MIENG("Miếng");

    private final String displayName;

    MaterialUnit(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
