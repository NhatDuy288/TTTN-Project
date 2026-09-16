package com.tttn.qlnvl.warehousetransfer.domain;

import com.tttn.qlnvl.inventory.domain.InventoryLot;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestDetail;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "warehouse_transfer_detail")
public class WarehouseTransferDetail {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false, updatable = false)
    private WarehouseTransfer transfer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_detail_id", nullable = false, unique = true, updatable = false)
    private WarehouseRequestDetail requestDetail;
    @OneToMany(mappedBy = "transferDetail", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransferLotAllocation> allocations = new ArrayList<>();

    protected WarehouseTransferDetail() {}
    WarehouseTransferDetail(WarehouseTransfer transfer, WarehouseRequestDetail requestDetail) {
        this.transfer = transfer; this.requestDetail = requestDetail;
    }

    public void allocate(InventoryLot lot, long quantity) {
        allocations.add(new TransferLotAllocation(this, lot, quantity, lot.getUnitPrice()));
    }
    public Long getId() { return id; }
    public WarehouseRequestDetail getRequestDetail() { return requestDetail; }
    public List<TransferLotAllocation> getAllocations() { return Collections.unmodifiableList(allocations); }
}
