package com.tttn.qlnvl.purchaseorder.application;

import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderStatus;
import java.time.LocalDate;

public record PurchaseOrderSearch(
        LocalDate createdFrom,
        LocalDate createdTo,
        PurchaseOrderStatus status,
        String poCode,
        String createdBy,
        Long materialGroupId,
        Long materialId,
        String supplierName) {
}
