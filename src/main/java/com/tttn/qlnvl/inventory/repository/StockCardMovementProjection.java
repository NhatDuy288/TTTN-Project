package com.tttn.qlnvl.inventory.repository;

import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import java.math.BigDecimal;
import java.time.Instant;

public interface StockCardMovementProjection {
    Instant getTransactionDate();
    String getDocumentCode();
    String getOperationTypeName();
    Long getWarehouseId();
    String getWarehouseCode();
    String getWarehouseName();
    Long getMaterialId();
    String getMaterialCode();
    String getMaterialName();
    MaterialCondition getCondition();
    String getReasonName();
    BigDecimal getUnitPrice();
    long getQuantity();
    String getPurchaseOrderCode();
    Long getLayerId();
}
