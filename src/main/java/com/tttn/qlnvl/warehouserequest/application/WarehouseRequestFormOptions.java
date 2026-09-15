package com.tttn.qlnvl.warehouserequest.application;

import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.Reason;
import java.util.List;

public record WarehouseRequestFormOptions(List<OperationOption> operations,
        List<Reason> reasons, List<Warehouse> warehouses, List<MaterialGroup> groups,
        List<Material> materials, List<PurchaseOrder> purchaseOrders) {
    public record OperationOption(Long id, String code, String name, OperationDirection direction,
            boolean requiresPo, String materialGroupIds, String conditions) {
    }
}
