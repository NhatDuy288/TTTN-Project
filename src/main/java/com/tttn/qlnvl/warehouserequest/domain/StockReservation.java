package com.tttn.qlnvl.warehouserequest.domain;

import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
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
import java.time.Instant;

@Entity
@Table(name = "stock_reservation")
public class StockReservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_detail_id", nullable = false, unique = true, updatable = false)
    private WarehouseRequestDetail requestDetail;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false, updatable = false)
    private Material material;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private MaterialCondition condition;
    @Column(nullable = false, updatable = false)
    private long quantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockReservationStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "released_at")
    private Instant releasedAt;

    protected StockReservation() {}

    public StockReservation(WarehouseRequestDetail requestDetail, Warehouse warehouse) {
        this.requestDetail = requestDetail;
        this.warehouse = warehouse;
        this.material = requestDetail.getMaterial();
        this.condition = requestDetail.getCondition();
        this.quantity = requestDetail.getQuantity();
        this.status = StockReservationStatus.ACTIVE_SOFT;
    }

    @PrePersist
    void initializeCreatedAt() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public WarehouseRequestDetail getRequestDetail() { return requestDetail; }
    public Warehouse getWarehouse() { return warehouse; }
    public Material getMaterial() { return material; }
    public MaterialCondition getCondition() { return condition; }
    public long getQuantity() { return quantity; }
    public StockReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReleasedAt() { return releasedAt; }
}
