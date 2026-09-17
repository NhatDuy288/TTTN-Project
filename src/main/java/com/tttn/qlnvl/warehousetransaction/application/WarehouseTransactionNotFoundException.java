package com.tttn.qlnvl.warehousetransaction.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class WarehouseTransactionNotFoundException extends RuntimeException {
    public WarehouseTransactionNotFoundException() {
        super("Không tìm thấy chứng từ nhập/xuất kho.");
    }
}
