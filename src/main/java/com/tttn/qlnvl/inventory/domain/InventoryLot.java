package com.tttn.qlnvl.inventory.domain;

import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
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
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "inventory_lot")
public class InventoryLot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false, updatable = false)
    private Material material;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private MaterialCondition condition;
    @Column(name = "on_hand_quantity", nullable = false)
    private long onHandQuantity;
    @Column(name = "reserved_quantity", nullable = false)
    private long reservedQuantity;
    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected InventoryLot() {}

    public Long getId() { return id; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public long getOnHandQuantity() { return onHandQuantity; }
    public long getReservedQuantity() { return reservedQuantity; }
    public Instant getReceivedAt() { return receivedAt; }

    public void reserve(long quantity) {
        if (quantity <= 0 || quantity > onHandQuantity - reservedQuantity) {
            throw new IllegalArgumentException("Invalid lot reservation quantity");
        }
        reservedQuantity += quantity;
    }
}
