package com.tttn.qlnvl.warehouserequest.domain;

import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "warehouse_request_detail")
public class WarehouseRequestDetail {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private WarehouseRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaterialCondition condition;
    @Column(nullable = false)
    private long quantity;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_item_id")
    private PurchaseOrderItem purchaseOrderItem;

    protected WarehouseRequestDetail() {}
    WarehouseRequestDetail(WarehouseRequest request, Material material, MaterialCondition condition,
            long quantity, PurchaseOrderItem purchaseOrderItem) {
        this.request = request; this.material = material; this.condition = condition;
        this.quantity = quantity; this.purchaseOrderItem = purchaseOrderItem;
    }
    public Long getId() { return id; }
    public Material getMaterial() { return material; }
    public MaterialCondition getCondition() { return condition; }
    public long getQuantity() { return quantity; }
    public PurchaseOrderItem getPurchaseOrderItem() { return purchaseOrderItem; }
}
