package com.tttn.qlnvl.shared.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class MasterDataLifecycleConflictException extends RuntimeException {
    public MasterDataLifecycleConflictException(String message) {
        super(message);
    }
}
