package com.tttn.qlnvl.warehouserequest.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class WarehouseRequestNotFoundException extends RuntimeException {
    public WarehouseRequestNotFoundException() {
        super("Không tìm thấy phiếu đề nghị.");
    }
}
