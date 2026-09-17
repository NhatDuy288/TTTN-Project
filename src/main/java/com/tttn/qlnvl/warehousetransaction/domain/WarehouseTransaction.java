package com.tttn.qlnvl.warehousetransaction.domain;

import com.tttn.qlnvl.auth.domain.AppUser;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "warehouse_transaction")
public class WarehouseTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "transaction_code", nullable = false, unique = true, length = 20, updatable = false)
    private String transactionCode;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, unique = true, updatable = false)
    private WarehouseRequest request;
    @Enumerated(EnumType.STRING)
    @Column(name = "warehouse_status", nullable = false, length = 40)
    private WarehouseTransactionStatus status;
    @Column(name = "execution_note", length = 1000)
    private String executionNote;
    @Enumerated(EnumType.STRING)
    @Column(name = "accounting_status", length = 30)
    private AccountingStatus accountingStatus;
    @Column(name = "physical_confirmed_at")
    private Instant physicalConfirmedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "physical_confirmed_by_user_id")
    private AppUser physicalConfirmedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WarehouseTransactionDetail> details = new ArrayList<>();

    protected WarehouseTransaction() {}

    public WarehouseTransaction(WarehouseRequest request) {
        OperationDirection direction = request.getOperationType().getDirection();
        if (direction == OperationDirection.TRANSFER) {
            throw new IllegalArgumentException("Transfer request requires WarehouseTransfer");
        }
        this.request = request;
        this.transactionCode = (direction == OperationDirection.IMPORT ? "GRN_" : "GDN_")
                + request.getRequestCode();
        this.status = WarehouseTransactionStatus.DRAFT;
        request.getDetails().forEach(detail -> details.add(new WarehouseTransactionDetail(this, detail)));
    }

    @PrePersist void initializeTimestamps() {
        Instant now = Instant.now(); createdAt = now; updatedAt = now;
    }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }

    public void submit(String executionNote) {
        this.executionNote = executionNote;
        this.status = WarehouseTransactionStatus.SUBMITTED;
    }

    public void approve() { this.status = WarehouseTransactionStatus.READY_FOR_CONFIRMATION; }
    public void reject() { this.status = WarehouseTransactionStatus.REJECTED; }
    public void confirmPhysical(AppUser actor, Instant confirmedAt, boolean requiresAccounting) {
        this.physicalConfirmedBy = actor;
        this.physicalConfirmedAt = confirmedAt;
        this.accountingStatus = requiresAccounting
                ? AccountingStatus.PENDING : AccountingStatus.NOT_REQUIRED;
        this.status = WarehouseTransactionStatus.COMPLETED;
    }

    public Long getId() { return id; }
    public String getTransactionCode() { return transactionCode; }
    public WarehouseRequest getRequest() { return request; }
    public WarehouseTransactionStatus getStatus() { return status; }
    public String getExecutionNote() { return executionNote; }
    public AccountingStatus getAccountingStatus() { return accountingStatus; }
    public Instant getPhysicalConfirmedAt() { return physicalConfirmedAt; }
    public AppUser getPhysicalConfirmedBy() { return physicalConfirmedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<WarehouseTransactionDetail> getDetails() { return Collections.unmodifiableList(details); }
}
