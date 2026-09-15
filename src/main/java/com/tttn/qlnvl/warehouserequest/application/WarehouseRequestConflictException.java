package com.tttn.qlnvl.warehouserequest.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class WarehouseRequestConflictException extends RuntimeException {
    public WarehouseRequestConflictException(String message) { super(message); }
}
