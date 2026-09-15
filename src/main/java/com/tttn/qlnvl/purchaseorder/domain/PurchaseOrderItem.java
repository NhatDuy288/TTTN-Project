package com.tttn.qlnvl.purchaseorder.domain;

import com.tttn.qlnvl.material.domain.Material;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_item")
public class PurchaseOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;
    @Column(name = "ordered_quantity", nullable = false)
    private long orderedQuantity;
    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    protected PurchaseOrderItem() {
    }

    PurchaseOrderItem(PurchaseOrder purchaseOrder, Material material, long orderedQuantity, BigDecimal unitPrice) {
        this.purchaseOrder = purchaseOrder;
        this.material = material;
        this.orderedQuantity = orderedQuantity;
        this.unitPrice = unitPrice;
    }

    public Long getId() { return id; }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public Material getMaterial() { return material; }
    public long getOrderedQuantity() { return orderedQuantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
