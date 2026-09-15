package com.tttn.qlnvl.warehouse.domain;

import com.tttn.qlnvl.auth.domain.AppUser;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "warehouse")
public class Warehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "warehouse_code", nullable = false, unique = true, length = 30)
    private String warehouseCode;
    @Column(name = "warehouse_name", nullable = false, unique = true, length = 50)
    private String warehouseName;
    @Column(nullable = false, length = 200)
    private String address;
    @Column(length = 200)
    private String note;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WarehouseStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by_user_id", nullable = false)
    private AppUser updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Warehouse() {
    }

    public Warehouse(String warehouseCode, String warehouseName, String address, String note, AppUser actor) {
        this.warehouseCode = warehouseCode;
        this.warehouseName = warehouseName;
        this.address = address;
        this.note = note;
        this.status = WarehouseStatus.ACTIVE;
        this.updatedBy = actor;
    }

    public void update(String warehouseName, String address, String note, WarehouseStatus status, AppUser actor) {
        this.warehouseName = warehouseName;
        this.address = address;
        this.note = note;
        this.status = status;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getWarehouseCode() { return warehouseCode; }
    public String getWarehouseName() { return warehouseName; }
    public String getAddress() { return address; }
    public String getNote() { return note; }
    public WarehouseStatus getStatus() { return status; }
    public AppUser getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
