package com.tttn.qlnvl.warehouserequest.application;

public class InvalidWarehouseRequestException extends RuntimeException {
    private final String field;
    public InvalidWarehouseRequestException(String field, String message) {
        super(message); this.field = field;
    }
    public String getField() { return field; }
}
