package com.tttn.qlnvl.shared.audit;

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
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "status_history")
public class StatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, length = 30, updatable = false)
    private AggregateType aggregateType;
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;
    @Column(name = "from_status", length = 40, updatable = false)
    private String fromStatus;
    @Column(name = "to_status", nullable = false, length = 40, updatable = false)
    private String toStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private WorkflowAction action;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_user_id", nullable = false, updatable = false)
    private AppUser changedBy;
    @Column(length = 500, updatable = false)
    private String comment;
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected StatusHistory() {}

    public StatusHistory(AggregateType aggregateType, Long aggregateId, String fromStatus,
            String toStatus, WorkflowAction action, AppUser changedBy, String comment) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.action = action;
        this.changedBy = changedBy;
        this.comment = comment;
    }

    @PrePersist
    void initializeChangedAt() {
        changedAt = Instant.now();
    }

    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public WorkflowAction getAction() { return action; }
    public AppUser getChangedBy() { return changedBy; }
    public String getComment() { return comment; }
    public Instant getChangedAt() { return changedAt; }
}
