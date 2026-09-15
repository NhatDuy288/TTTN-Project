package com.tttn.qlnvl.material.domain;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "material")
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "material_code", nullable = false, unique = true, length = 30)
    private String materialCode;
    @Column(name = "material_name", nullable = false, length = 200)
    private String materialName;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_group_id", nullable = false)
    private MaterialGroup materialGroup;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaterialUnit unit;
    @Column(name = "gl_code", length = 200)
    private String glCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaterialStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Material() {
    }

    public Material(String materialCode, String materialName, MaterialGroup materialGroup,
                    MaterialUnit unit, String glCode) {
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.materialGroup = materialGroup;
        this.unit = unit;
        this.glCode = glCode;
        this.status = MaterialStatus.ACTIVE;
    }

    public void update(String materialName, MaterialUnit unit, String glCode, MaterialStatus status) {
        this.materialName = materialName;
        this.unit = unit;
        this.glCode = glCode;
        this.status = status;
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
    public String getMaterialCode() { return materialCode; }
    public String getMaterialName() { return materialName; }
    public MaterialGroup getMaterialGroup() { return materialGroup; }
    public MaterialUnit getUnit() { return unit; }
    public String getGlCode() { return glCode; }
    public MaterialStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
