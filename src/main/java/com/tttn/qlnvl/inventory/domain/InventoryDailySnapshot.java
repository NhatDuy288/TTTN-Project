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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_daily_snapshot", uniqueConstraints = @UniqueConstraint(
        name = "uk_inventory_daily_snapshot_dimension",
        columnNames = {"snapshot_date", "warehouse_id", "material_id", "condition"}))
public class InventoryDailySnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "snapshot_date", nullable = false, updatable = false)
    private LocalDate snapshotDate;
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
    @Column(name = "available_quantity", nullable = false)
    private long availableQuantity;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryDailySnapshot() {}

    public InventoryDailySnapshot(LocalDate snapshotDate, Warehouse warehouse, Material material,
            MaterialCondition condition, long onHandQuantity, long reservedQuantity) {
        this.snapshotDate = snapshotDate;
        this.warehouse = warehouse;
        this.material = material;
        this.condition = condition;
        updateQuantities(onHandQuantity, reservedQuantity);
    }

    public void updateQuantities(long onHandQuantity, long reservedQuantity) {
        if (onHandQuantity < 0 || reservedQuantity < 0) {
            throw new IllegalArgumentException("Snapshot quantities cannot be negative");
        }
        this.onHandQuantity = onHandQuantity;
        this.reservedQuantity = reservedQuantity;
        this.availableQuantity = onHandQuantity - reservedQuantity;
    }

    @PrePersist
    void initializeCreatedAt() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public Warehouse getWarehouse() { return warehouse; }
    public Material getMaterial() { return material; }
    public MaterialCondition getCondition() { return condition; }
    public long getOnHandQuantity() { return onHandQuantity; }
    public long getReservedQuantity() { return reservedQuantity; }
    public long getAvailableQuantity() { return availableQuantity; }
    public Instant getCreatedAt() { return createdAt; }
}
