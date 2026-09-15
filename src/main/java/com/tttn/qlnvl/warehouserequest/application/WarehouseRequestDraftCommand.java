package com.tttn.qlnvl.warehouserequest.application;

import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import java.util.List;

public record WarehouseRequestDraftCommand(Long operationTypeId, Long reasonId,
        Long sourceWarehouseId, Long destinationWarehouseId, Long purchaseOrderId,
        String note, List<Detail> details) {
    public record Detail(Long materialId, MaterialCondition condition, Long quantity,
                         Long purchaseOrderItemId) {
        public boolean isBlank() {
            return materialId == null && condition == null && quantity == null
                    && purchaseOrderItemId == null;
        }
    }
}
