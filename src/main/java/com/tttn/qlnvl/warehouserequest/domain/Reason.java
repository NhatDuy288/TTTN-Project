package com.tttn.qlnvl.warehouserequest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reason")
public class Reason {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperationDirection direction;
    @Column(name = "requires_note", nullable = false)
    private boolean requiresNote;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected Reason() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public OperationDirection getDirection() { return direction; }
    public boolean isRequiresNote() { return requiresNote; }
    public boolean isActive() { return active; }
    public int getDisplayOrder() { return displayOrder; }
}
