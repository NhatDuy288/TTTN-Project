package com.tttn.qlnvl.warehousetransaction.application;

public class InvalidWarehouseTransactionException extends RuntimeException {
    private final String field;

    public InvalidWarehouseTransactionException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
