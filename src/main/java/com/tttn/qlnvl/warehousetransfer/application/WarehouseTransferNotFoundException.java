package com.tttn.qlnvl.warehousetransfer.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class WarehouseTransferNotFoundException extends RuntimeException {
    public WarehouseTransferNotFoundException() {
        super("Không tìm thấy chứng từ điều chuyển kho.");
    }
}
