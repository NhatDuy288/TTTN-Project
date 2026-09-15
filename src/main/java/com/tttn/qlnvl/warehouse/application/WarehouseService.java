package com.tttn.qlnvl.warehouse.application;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.shared.application.DuplicateMasterDataException;
import com.tttn.qlnvl.shared.application.InvalidMasterDataException;
import com.tttn.qlnvl.shared.application.MasterDataLifecycleConflictException;
import com.tttn.qlnvl.shared.application.MasterDataNotFoundException;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.domain.WarehouseStatus;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseService {
    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(20, 50, 100);

    private final WarehouseRepository warehouseRepository;
    private final AppUserRepository appUserRepository;

    public WarehouseService(WarehouseRepository warehouseRepository, AppUserRepository appUserRepository) {
        this.warehouseRepository = warehouseRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public Page<Warehouse> search(String keyword, WarehouseStatus status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 20;
        return warehouseRepository.search(searchTerm(keyword), status,
                PageRequest.of(safePage, safeSize, Sort.by("warehouseCode").ascending()));
    }

    @Transactional(readOnly = true)
    public Warehouse get(Long id) {
        return warehouseRepository.findDetailedById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Không tìm thấy kho."));
    }

    @Transactional
    public Warehouse create(String code, String name, String address, String note, Long actorId) {
        String normalizedCode = requiredTrimmed(code, "warehouseCode", "Mã kho là bắt buộc.")
                .toUpperCase(Locale.ROOT);
        String normalizedName = requiredTrimmed(name, "warehouseName", "Tên kho là bắt buộc.");
        String normalizedAddress = requiredTrimmed(address, "address", "Địa chỉ là bắt buộc.");
        String normalizedNote = blankToNull(note);
        validateFields(normalizedCode, normalizedName, normalizedAddress, normalizedNote, null);
        if (warehouseRepository.existsByWarehouseCodeIgnoreCase(normalizedCode)) {
            throw new DuplicateMasterDataException("warehouseCode", "Mã kho đã tồn tại.");
        }
        if (warehouseRepository.existsByWarehouseName(normalizedName)) {
            throw new DuplicateMasterDataException("warehouseName", "Tên kho đã tồn tại.");
        }
        AppUser actor = actor(actorId);
        try {
            return warehouseRepository.saveAndFlush(new Warehouse(
                    normalizedCode, normalizedName, normalizedAddress, normalizedNote, actor));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateMasterDataException(null, "Mã kho hoặc tên kho đã tồn tại.");
        }
    }

    @Transactional
    public Warehouse update(Long id, String name, String address, String note,
                            WarehouseStatus status, Long actorId) {
        Warehouse warehouse = warehouseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Không tìm thấy kho."));
        String normalizedName = requiredTrimmed(name, "warehouseName", "Tên kho là bắt buộc.");
        String normalizedAddress = requiredTrimmed(address, "address", "Địa chỉ là bắt buộc.");
        String normalizedNote = blankToNull(note);
        validateFields(null, normalizedName, normalizedAddress, normalizedNote, status);
        if (warehouseRepository.existsByWarehouseNameAndIdNot(normalizedName, id)) {
            throw new DuplicateMasterDataException("warehouseName", "Tên kho đã tồn tại.");
        }
        if (warehouse.getStatus() == WarehouseStatus.ACTIVE && status == WarehouseStatus.INACTIVE
                && (warehouseRepository.hasOnHandStock(id) || warehouseRepository.hasActiveWorkflow(id))) {
            throw new MasterDataLifecycleConflictException(
                    "Không thể ngừng hoạt động kho khi còn tồn kho hoặc workflow đang xử lý.");
        }
        warehouse.update(normalizedName, normalizedAddress, normalizedNote, status, actor(actorId));
        try {
            return warehouseRepository.saveAndFlush(warehouse);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateMasterDataException("warehouseName", "Tên kho đã tồn tại.");
        }
    }

    private AppUser actor(Long actorId) {
        return appUserRepository.findById(actorId)
                .orElseThrow(() -> new MasterDataNotFoundException("Không tìm thấy người thao tác."));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requiredTrimmed(String value, String field, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new InvalidMasterDataException(field, message);
        }
        return normalized;
    }

    private void validateFields(String code, String name, String address, String note, WarehouseStatus status) {
        if (code != null && (code.length() > 30
                || !code.matches("KHO_[A-Z0-9]+(?:_[A-Z0-9]+)*_[0-9]{2}"))) {
            throw new InvalidMasterDataException(
                    "warehouseCode", "Mã kho phải theo mẫu KHO_<DIA_DIEM>_<NN>, viết hoa, không dấu và tối đa 30 ký tự.");
        }
        if (name.length() < 5 || name.length() > 50) {
            throw new InvalidMasterDataException("warehouseName", "Tên kho phải từ 5 đến 50 ký tự.");
        }
        if (address.length() < 20 || address.length() > 200) {
            throw new InvalidMasterDataException("address", "Địa chỉ phải từ 20 đến 200 ký tự.");
        }
        if (note != null && note.length() > 200) {
            throw new InvalidMasterDataException("note", "Ghi chú tối đa 200 ký tự.");
        }
        if (code == null && status == null) {
            throw new InvalidMasterDataException("status", "Vui lòng chọn trạng thái.");
        }
    }

    private String searchTerm(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
