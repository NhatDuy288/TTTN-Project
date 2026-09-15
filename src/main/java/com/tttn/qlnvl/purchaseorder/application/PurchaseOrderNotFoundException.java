package com.tttn.qlnvl.purchaseorder.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PurchaseOrderNotFoundException extends RuntimeException {
    public PurchaseOrderNotFoundException() {
        super("Không tìm thấy đơn hàng.");
    }
}
