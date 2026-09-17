package com.tttn.qlnvl.warehousetransfer.application;

public class InvalidWarehouseTransferException extends RuntimeException {
    private final String field;

    public InvalidWarehouseTransferException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
