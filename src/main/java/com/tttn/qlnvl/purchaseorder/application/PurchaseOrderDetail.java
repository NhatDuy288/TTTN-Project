package com.tttn.qlnvl.purchaseorder.application;

import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import java.util.List;

public record PurchaseOrderDetail(PurchaseOrder order, List<Item> items) {
    public record Item(PurchaseOrderItem orderItem, long committedQuantity, long receivedQuantity) {
        public long getRemainingToRequest() {
            return orderItem.getOrderedQuantity() - committedQuantity;
        }

        public long getRemainingToReceive() {
            return orderItem.getOrderedQuantity() - receivedQuantity;
        }
    }
}
