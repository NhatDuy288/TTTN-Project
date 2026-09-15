package com.tttn.qlnvl.shared.application;

public class DuplicateMasterDataException extends RuntimeException {
    private final String field;

    public DuplicateMasterDataException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
