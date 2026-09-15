package com.tttn.qlnvl.purchaseorder.application;

public class InvalidPurchaseOrderException extends RuntimeException {
    private final String field;

    public InvalidPurchaseOrderException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
