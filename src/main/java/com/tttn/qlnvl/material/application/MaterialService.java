package com.tttn.qlnvl.material.application;

import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.domain.MaterialStatus;
import com.tttn.qlnvl.material.domain.MaterialUnit;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.shared.application.DuplicateMasterDataException;
import com.tttn.qlnvl.shared.application.InvalidMasterDataException;
import com.tttn.qlnvl.shared.application.MasterDataLifecycleConflictException;
import com.tttn.qlnvl.shared.application.MasterDataNotFoundException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialService {
    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(20, 50, 100);

    private final MaterialRepository materialRepository;
    private final MaterialGroupRepository materialGroupRepository;

    public MaterialService(MaterialRepository materialRepository, MaterialGroupRepository materialGroupRepository) {
        this.materialRepository = materialRepository;
        this.materialGroupRepository = materialGroupRepository;
    }

    @Transactional(readOnly = true)
    public Page<Material> search(Long groupId, String codeOrName, MaterialStatus status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 20;
        return materialRepository.search(groupId, searchTerm(codeOrName), status,
                PageRequest.of(safePage, safeSize, Sort.by("materialCode").ascending()));
    }

    @Transactional(readOnly = true)
    public Material get(Long id) {
        return materialRepository.findDetailedById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Không tìm thấy vật tư."));
    }

    @Transactional(readOnly = true)
    public List<MaterialGroup> allGroups() {
        return materialGroupRepository.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<MaterialGroup> activeGroups() {
        return materialGroupRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional
    public Material create(Long groupId, String code, String name, MaterialUnit unit, String glCode) {
        String normalizedCode = requiredTrimmed(code, "materialCode", "Mã vật tư là bắt buộc.");
        String normalizedName = requiredTrimmed(name, "materialName", "Tên vật tư là bắt buộc.");
        String normalizedGl = blankToNull(glCode);
        validateFields(normalizedCode, normalizedName, unit, normalizedGl);
        if (groupId == null) {
            throw new InvalidMasterDataException("materialGroupId", "Vui lòng chọn nhóm vật tư.");
        }
        MaterialGroup group = materialGroupRepository.findById(groupId)
                .filter(MaterialGroup::isActive)
                .orElseThrow(() -> new InvalidMasterDataException(
                        "materialGroupId", "Nhóm vật tư không tồn tại hoặc không hoạt động."));

        if (materialRepository.existsByMaterialCode(normalizedCode)) {
            throw new DuplicateMasterDataException("materialCode", "Mã vật tư đã tồn tại.");
        }
        validateGlCode(group, normalizedGl, MaterialStatus.ACTIVE, null);

        try {
            return materialRepository.saveAndFlush(new Material(
                    normalizedCode, normalizedName, group, unit, normalizedGl));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateMasterDataException(null, "Mã vật tư hoặc GL Code đang hoạt động đã tồn tại.");
        }
    }

    @Transactional
    public Material update(Long id, String name, MaterialUnit unit, String glCode, MaterialStatus status) {
        Material material = materialRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Không tìm thấy vật tư."));
        String normalizedName = requiredTrimmed(name, "materialName", "Tên vật tư là bắt buộc.");
        String normalizedGl = blankToNull(glCode);
        validateFields(null, normalizedName, unit, normalizedGl);
        if (status == null) {
            throw new InvalidMasterDataException("status", "Vui lòng chọn trạng thái.");
        }

        if (material.getStatus() == MaterialStatus.ACTIVE && status == MaterialStatus.INACTIVE
                && (materialRepository.hasOnHandStock(id) || materialRepository.hasActiveWorkflow(id))) {
            throw new MasterDataLifecycleConflictException(
                    "Không thể ngừng hoạt động vật tư khi còn tồn kho hoặc workflow đang xử lý.");
        }
        validateGlCode(material.getMaterialGroup(), normalizedGl, status, id);
        material.update(normalizedName, unit, normalizedGl, status);
        try {
            return materialRepository.saveAndFlush(material);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateMasterDataException("glCode", "GL Code đang gắn với vật tư hoạt động khác.");
        }
    }

    private void validateGlCode(MaterialGroup group, String glCode, MaterialStatus status, Long currentId) {
        if (group.isRequiresAccounting() && glCode == null) {
            throw new InvalidMasterDataException("glCode", "GL Code là bắt buộc với nhóm cần hạch toán.");
        }
        if (glCode != null && !glCode.matches("[A-Za-z0-9-]+")) {
            throw new InvalidMasterDataException("glCode", "GL Code chỉ gồm chữ, số và dấu '-'.");
        }
        if (status == MaterialStatus.ACTIVE && glCode != null) {
            boolean duplicate = currentId == null
                    ? materialRepository.existsByGlCodeAndStatus(glCode, MaterialStatus.ACTIVE)
                    : materialRepository.existsByGlCodeAndStatusAndIdNot(
                            glCode, MaterialStatus.ACTIVE, currentId);
            if (duplicate) {
                throw new DuplicateMasterDataException(
                        "glCode", "GL Code đang gắn với vật tư hoạt động khác.");
            }
        }
    }

    private void validateFields(String code, String name, MaterialUnit unit, String glCode) {
        if (code != null && (code.length() > 30 || !code.matches("[A-Za-z0-9_]+"))) {
            throw new InvalidMasterDataException(
                    "materialCode", "Mã vật tư chỉ gồm chữ, số, dấu '_' và tối đa 30 ký tự.");
        }
        if (name.length() > 200) {
            throw new InvalidMasterDataException("materialName", "Tên vật tư tối đa 200 ký tự.");
        }
        if (unit == null) {
            throw new InvalidMasterDataException("unit", "Vui lòng chọn đơn vị tính.");
        }
        if (glCode != null && glCode.length() > 200) {
            throw new InvalidMasterDataException("glCode", "GL Code tối đa 200 ký tự.");
        }
    }

    private String requiredTrimmed(String value, String field, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new InvalidMasterDataException(field, message);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String searchTerm(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
