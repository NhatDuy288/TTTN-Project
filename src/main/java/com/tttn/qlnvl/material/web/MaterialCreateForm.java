package com.tttn.qlnvl.material.web;

import com.tttn.qlnvl.material.domain.MaterialUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class MaterialCreateForm {
    @NotNull(message = "Vui lòng chọn nhóm vật tư.")
    private Long materialGroupId;
    @NotBlank(message = "Mã vật tư là bắt buộc.")
    @Size(max = 30, message = "Mã vật tư tối đa 30 ký tự.")
    @Pattern(regexp = "[A-Za-z0-9_]+", message = "Mã vật tư chỉ gồm chữ, số và dấu '_'.")
    private String materialCode;
    @NotBlank(message = "Tên vật tư là bắt buộc.")
    @Size(max = 200, message = "Tên vật tư tối đa 200 ký tự.")
    private String materialName;
    @NotNull(message = "Vui lòng chọn đơn vị tính.")
    private MaterialUnit unit;
    @Size(max = 200, message = "GL Code tối đa 200 ký tự.")
    @Pattern(regexp = "[A-Za-z0-9-]*", message = "GL Code chỉ gồm chữ, số và dấu '-'.")
    private String glCode;

    public Long getMaterialGroupId() { return materialGroupId; }
    public void setMaterialGroupId(Long materialGroupId) { this.materialGroupId = materialGroupId; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public MaterialUnit getUnit() { return unit; }
    public void setUnit(MaterialUnit unit) { this.unit = unit; }
    public String getGlCode() { return glCode; }
    public void setGlCode(String glCode) { this.glCode = glCode; }
}
