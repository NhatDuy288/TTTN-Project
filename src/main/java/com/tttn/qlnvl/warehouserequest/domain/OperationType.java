package com.tttn.qlnvl.warehouserequest.domain;

import com.tttn.qlnvl.material.domain.MaterialGroup;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "operation_type")
public class OperationType {
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
    @Column(name = "requires_po", nullable = false)
    private boolean requiresPo;
    @Column(name = "requires_accounting", nullable = false)
    private boolean requiresAccounting;
    @Enumerated(EnumType.STRING)
    @Column(name = "default_condition", length = 20)
    private MaterialCondition defaultCondition;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "operation_type_material_group",
            joinColumns = @JoinColumn(name = "operation_type_id"),
            inverseJoinColumns = @JoinColumn(name = "material_group_id"))
    private Set<MaterialGroup> materialGroups = new LinkedHashSet<>();
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "operation_type_condition",
            joinColumns = @JoinColumn(name = "operation_type_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "condition", nullable = false, length = 20)
    private Set<MaterialCondition> allowedConditions = new LinkedHashSet<>();

    protected OperationType() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public OperationDirection getDirection() { return direction; }
    public boolean isRequiresPo() { return requiresPo; }
    public boolean isRequiresAccounting() { return requiresAccounting; }
    public MaterialCondition getDefaultCondition() { return defaultCondition; }
    public boolean isActive() { return active; }
    public int getDisplayOrder() { return displayOrder; }
    public Set<MaterialGroup> getMaterialGroups() {
        return Collections.unmodifiableSet(materialGroups);
    }
    public Set<MaterialCondition> getAllowedConditions() {
        return Collections.unmodifiableSet(allowedConditions);
    }
}
