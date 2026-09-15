package com.tttn.qlnvl.shared.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class MasterDataNotFoundException extends RuntimeException {
    public MasterDataNotFoundException(String message) {
        super(message);
    }
}
