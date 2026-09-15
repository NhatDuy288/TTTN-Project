package com.tttn.qlnvl.purchaseorder.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class PurchaseOrderConflictException extends RuntimeException {
    public PurchaseOrderConflictException(String message) {
        super(message);
    }
}
