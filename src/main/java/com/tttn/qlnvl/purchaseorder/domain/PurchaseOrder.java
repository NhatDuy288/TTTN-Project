package com.tttn.qlnvl.purchaseorder.domain;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.material.domain.Material;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "po_code", nullable = false, unique = true, length = 50, updatable = false)
    private String poCode;
    @Column(name = "supplier_name", nullable = false, length = 300)
    private String supplierName;
    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;
    @Column(name = "first_received_at")
    private Instant firstReceivedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseOrderStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private AppUser createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String poCode, String supplierName, LocalDate orderDate, AppUser createdBy) {
        this.poCode = poCode;
        this.supplierName = supplierName;
        this.orderDate = orderDate;
        this.createdBy = createdBy;
        this.status = PurchaseOrderStatus.DRAFT;
    }

    public void updateDraft(String supplierName, LocalDate orderDate) {
        this.supplierName = supplierName;
        this.orderDate = orderDate;
    }

    public void replaceItems(List<ItemDefinition> definitions) {
        items.clear();
        addItems(definitions);
    }

    public void clearItems() {
        items.clear();
    }

    public void addItems(List<ItemDefinition> definitions) {
        definitions.forEach(definition -> items.add(new PurchaseOrderItem(
                this, definition.material(), definition.orderedQuantity(), definition.unitPrice())));
    }

    public void finalizeOrder() {
        status = PurchaseOrderStatus.OPEN;
    }

    public void cancel() {
        status = PurchaseOrderStatus.CANCELLED;
    }

    public void registerReceipt(Instant receivedAt, boolean fullyReceived) {
        if (firstReceivedAt == null) firstReceivedAt = receivedAt;
        status = fullyReceived ? PurchaseOrderStatus.COMPLETED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED;
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
    public String getPoCode() { return poCode; }
    public String getSupplierName() { return supplierName; }
    public LocalDate getOrderDate() { return orderDate; }
    public Instant getFirstReceivedAt() { return firstReceivedAt; }
    public PurchaseOrderStatus getStatus() { return status; }
    public AppUser getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<PurchaseOrderItem> getItems() { return Collections.unmodifiableList(items); }

    public record ItemDefinition(Material material, long orderedQuantity, BigDecimal unitPrice) {
    }
}
