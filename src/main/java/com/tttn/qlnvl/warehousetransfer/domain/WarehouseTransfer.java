package com.tttn.qlnvl.warehousetransfer.domain;

import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "warehouse_transfer")
public class WarehouseTransfer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, unique = true, updatable = false)
    private WarehouseRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_warehouse_id", nullable = false, updatable = false)
    private Warehouse sourceWarehouse;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_warehouse_id", nullable = false, updatable = false)
    private Warehouse destinationWarehouse;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WarehouseTransferStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WarehouseTransferDetail> details = new ArrayList<>();

    protected WarehouseTransfer() {}

    public WarehouseTransfer(WarehouseRequest request) {
        if (request.getOperationType().getDirection() != OperationDirection.TRANSFER) {
            throw new IllegalArgumentException("Non-transfer request requires WarehouseTransaction");
        }
        this.request = request;
        this.sourceWarehouse = request.getSourceWarehouse();
        this.destinationWarehouse = request.getDestinationWarehouse();
        this.status = WarehouseTransferStatus.DRAFT;
        request.getDetails().forEach(detail -> details.add(new WarehouseTransferDetail(this, detail)));
    }

    @PrePersist void initializeTimestamps() {
        Instant now = Instant.now(); createdAt = now; updatedAt = now;
    }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }

    public void submit() { this.status = WarehouseTransferStatus.SUBMITTED; }

    public Long getId() { return id; }
    public WarehouseRequest getRequest() { return request; }
    public Warehouse getSourceWarehouse() { return sourceWarehouse; }
    public Warehouse getDestinationWarehouse() { return destinationWarehouse; }
    public WarehouseTransferStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<WarehouseTransferDetail> getDetails() { return Collections.unmodifiableList(details); }
}
