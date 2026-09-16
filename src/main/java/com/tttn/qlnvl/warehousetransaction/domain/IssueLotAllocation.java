package com.tttn.qlnvl.warehousetransaction.domain;

import com.tttn.qlnvl.inventory.domain.InventoryLot;
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
@Table(name = "issue_lot_allocation")
public class IssueLotAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_detail_id", nullable = false, updatable = false)
    private WarehouseTransactionDetail transactionDetail;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_lot_id", nullable = false, updatable = false)
    private InventoryLot inventoryLot;
    @Column(name = "allocated_quantity", nullable = false, updatable = false)
    private long allocatedQuantity;
    @Column(name = "allocated_unit_price", precision = 19, scale = 4, updatable = false)
    private BigDecimal allocatedUnitPrice;

    protected IssueLotAllocation() {}
    IssueLotAllocation(WarehouseTransactionDetail detail, InventoryLot lot,
            long quantity, BigDecimal unitPrice) {
        this.transactionDetail = detail; this.inventoryLot = lot;
        this.allocatedQuantity = quantity; this.allocatedUnitPrice = unitPrice;
    }
}
