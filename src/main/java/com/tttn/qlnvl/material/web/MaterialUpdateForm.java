package com.tttn.qlnvl.material.web;

import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialStatus;
import com.tttn.qlnvl.material.domain.MaterialUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class MaterialUpdateForm {
    @NotBlank(message = "Tên vật tư là bắt buộc.")
    @Size(max = 200, message = "Tên vật tư tối đa 200 ký tự.")
    private String materialName;
    @NotNull(message = "Vui lòng chọn đơn vị tính.")
    private MaterialUnit unit;
    @Size(max = 200, message = "GL Code tối đa 200 ký tự.")
    @Pattern(regexp = "[A-Za-z0-9-]*", message = "GL Code chỉ gồm chữ, số và dấu '-'.")
    private String glCode;
    @NotNull(message = "Vui lòng chọn trạng thái.")
    private MaterialStatus status;

    public static MaterialUpdateForm from(Material material) {
        MaterialUpdateForm form = new MaterialUpdateForm();
        form.materialName = material.getMaterialName();
        form.unit = material.getUnit();
        form.glCode = material.getGlCode();
        form.status = material.getStatus();
        return form;
    }

    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public MaterialUnit getUnit() { return unit; }
    public void setUnit(MaterialUnit unit) { this.unit = unit; }
    public String getGlCode() { return glCode; }
    public void setGlCode(String glCode) { this.glCode = glCode; }
    public MaterialStatus getStatus() { return status; }
    public void setStatus(MaterialStatus status) { this.status = status; }
}
