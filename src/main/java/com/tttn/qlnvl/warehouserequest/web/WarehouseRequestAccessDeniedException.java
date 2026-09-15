package com.tttn.qlnvl.warehouserequest.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
class WarehouseRequestAccessDeniedException extends RuntimeException {
}
