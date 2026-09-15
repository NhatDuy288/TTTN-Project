package com.tttn.qlnvl.shared.application;

public class InvalidMasterDataException extends RuntimeException {
    private final String field;

    public InvalidMasterDataException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
