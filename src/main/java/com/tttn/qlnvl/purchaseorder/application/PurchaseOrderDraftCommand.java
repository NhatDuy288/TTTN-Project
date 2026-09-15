package com.tttn.qlnvl.purchaseorder.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderDraftCommand(
        String poCode,
        String supplierName,
        LocalDate orderDate,
        List<Item> items) {

    public record Item(Long materialId, Long orderedQuantity, BigDecimal unitPrice) {
        public boolean isBlank() {
            return materialId == null && orderedQuantity == null && unitPrice == null;
        }
    }
}
