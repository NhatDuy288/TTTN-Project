package com.tttn.qlnvl.inventory.domain;

import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionDetail;
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_po_item_id", updatable = false)
    private PurchaseOrderItem sourcePurchaseOrderItem;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_transaction_detail_id", unique = true, updatable = false)
    private WarehouseTransactionDetail receiptTransactionDetail;

    protected InventoryLot() {}

    public InventoryLot(Warehouse warehouse, Material material, MaterialCondition condition,
            PurchaseOrderItem sourcePurchaseOrderItem,
            WarehouseTransactionDetail receiptTransactionDetail, BigDecimal unitPrice,
            long quantity, Instant receivedAt) {
        if (quantity <= 0) throw new IllegalArgumentException("Invalid receipt quantity");
        this.warehouse = warehouse;
        this.material = material;
        this.condition = condition;
        this.sourcePurchaseOrderItem = sourcePurchaseOrderItem;
        this.receiptTransactionDetail = receiptTransactionDetail;
        this.unitPrice = unitPrice;
        this.onHandQuantity = quantity;
        this.reservedQuantity = 0;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public long getOnHandQuantity() { return onHandQuantity; }
    public long getReservedQuantity() { return reservedQuantity; }
    public Instant getReceivedAt() { return receivedAt; }
    public Warehouse getWarehouse() { return warehouse; }
    public Material getMaterial() { return material; }
    public MaterialCondition getCondition() { return condition; }
    public PurchaseOrderItem getSourcePurchaseOrderItem() { return sourcePurchaseOrderItem; }
    public WarehouseTransactionDetail getReceiptTransactionDetail() {
        return receiptTransactionDetail;
    }

    public void reserve(long quantity) {
        if (quantity <= 0 || quantity > onHandQuantity - reservedQuantity) {
            throw new IllegalArgumentException("Invalid lot reservation quantity");
        }
        reservedQuantity += quantity;
    }

    public void releaseReservation(long quantity) {
        if (quantity <= 0 || quantity > reservedQuantity) {
            throw new IllegalArgumentException("Invalid lot reservation release quantity");
        }
        reservedQuantity -= quantity;
    }

    public void issue(long quantity) {
        if (quantity <= 0 || quantity > reservedQuantity || quantity > onHandQuantity) {
            throw new IllegalArgumentException("Invalid lot issue quantity");
        }
        onHandQuantity -= quantity;
        reservedQuantity -= quantity;
    }
}
