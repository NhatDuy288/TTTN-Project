package com.tttn.qlnvl.warehousetransaction.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class WarehouseTransactionConflictException extends RuntimeException {
    public WarehouseTransactionConflictException(String message) {
        super(message);
    }
}
