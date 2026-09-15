package com.tttn.qlnvl.purchaseorder.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
class PurchaseOrderAccessDeniedException extends RuntimeException {
}
