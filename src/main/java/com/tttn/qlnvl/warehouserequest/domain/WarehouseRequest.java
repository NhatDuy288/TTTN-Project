package com.tttn.qlnvl.warehouserequest.domain;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "warehouse_request")
public class WarehouseRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_code", nullable = false, unique = true, length = 16, updatable = false)
    private String requestCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_type_id", nullable = false, updatable = false)
    private OperationType operationType;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reason_id", nullable = false)
    private Reason reason;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_warehouse_id")
    private Warehouse sourceWarehouse;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_warehouse_id")
    private Warehouse destinationWarehouse;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", updatable = false)
    private PurchaseOrder purchaseOrder;
    @Column(length = 300)
    private String note;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WarehouseRequestStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private AppUser createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WarehouseRequestDetail> details = new ArrayList<>();

    protected WarehouseRequest() {}

    public WarehouseRequest(String requestCode, OperationType operationType, Reason reason,
            Warehouse sourceWarehouse, Warehouse destinationWarehouse, PurchaseOrder purchaseOrder,
            String note, AppUser createdBy) {
        this.requestCode = requestCode;
        this.operationType = operationType;
        this.reason = reason;
        this.sourceWarehouse = sourceWarehouse;
        this.destinationWarehouse = destinationWarehouse;
        this.purchaseOrder = purchaseOrder;
        this.note = note;
        this.createdBy = createdBy;
        this.status = WarehouseRequestStatus.DRAFT;
    }

    public void updateDraft(Reason reason, Warehouse sourceWarehouse,
            Warehouse destinationWarehouse, String note) {
        this.reason = reason;
        this.sourceWarehouse = sourceWarehouse;
        this.destinationWarehouse = destinationWarehouse;
        this.note = note;
    }

    public void submit() {
        this.status = WarehouseRequestStatus.SUBMITTED;
    }

    public void cancel() {
        this.status = WarehouseRequestStatus.CANCELLED;
    }

    public void approve() { this.status = WarehouseRequestStatus.APPROVED; }
    public void startProcessing() { this.status = WarehouseRequestStatus.PROCESSING; }
    public void complete() { this.status = WarehouseRequestStatus.COMPLETED; }
    public void reject() { this.status = WarehouseRequestStatus.REJECTED; }

    public void replaceDetails(List<DetailDefinition> definitions) {
        details.clear();
        definitions.forEach(definition -> details.add(new WarehouseRequestDetail(this,
                definition.material(), definition.condition(), definition.quantity(),
                definition.purchaseOrderItem())));
    }

    public void clearDetails() { details.clear(); }
    public void addDetails(List<DetailDefinition> definitions) {
        definitions.forEach(definition -> details.add(new WarehouseRequestDetail(this,
                definition.material(), definition.condition(), definition.quantity(),
                definition.purchaseOrderItem())));
    }

    @PrePersist void initializeTimestamps() {
        Instant now = Instant.now(); createdAt = now; updatedAt = now;
    }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getRequestCode() { return requestCode; }
    public OperationType getOperationType() { return operationType; }
    public Reason getReason() { return reason; }
    public Warehouse getSourceWarehouse() { return sourceWarehouse; }
    public Warehouse getDestinationWarehouse() { return destinationWarehouse; }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public String getNote() { return note; }
    public WarehouseRequestStatus getStatus() { return status; }
    public AppUser getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<WarehouseRequestDetail> getDetails() { return Collections.unmodifiableList(details); }

    public record DetailDefinition(Material material, MaterialCondition condition, long quantity,
                                   PurchaseOrderItem purchaseOrderItem) {}
}
