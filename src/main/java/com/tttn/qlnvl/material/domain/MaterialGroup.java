package com.tttn.qlnvl.material.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "material_group")
public class MaterialGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(name = "requires_accounting", nullable = false)
    private boolean requiresAccounting;
    @Column(nullable = false)
    private boolean active;

    protected MaterialGroup() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isRequiresAccounting() { return requiresAccounting; }
    public boolean isActive() { return active; }
}
