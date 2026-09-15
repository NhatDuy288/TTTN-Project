package com.tttn.qlnvl.purchaseorder.application;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.domain.MaterialStatus;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder.ItemDefinition;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderStatus;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderItemRepository;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderService {
    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(20, 50, 100);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository itemRepository;
    private final MaterialRepository materialRepository;
    private final MaterialGroupRepository materialGroupRepository;
    private final AppUserRepository appUserRepository;

    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository itemRepository,
            MaterialRepository materialRepository,
            MaterialGroupRepository materialGroupRepository,
            AppUserRepository appUserRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.itemRepository = itemRepository;
        this.materialRepository = materialRepository;
        this.materialGroupRepository = materialGroupRepository;
        this.appUserRepository = appUserRepository;
    }

    public PurchaseOrderSearch defaultSearch() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return new PurchaseOrderSearch(today.minusDays(7), today, null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrder> search(PurchaseOrderSearch requested, int page, int size) {
        PurchaseOrderSearch search = normalizeSearch(requested);
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 20;
        return purchaseOrderRepository.search(
                search.createdFrom().atStartOfDay(BUSINESS_ZONE).toInstant(),
                search.createdTo().plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant(),
                search.status(),
                searchTerm(search.poCode()),
                searchTerm(search.createdBy()),
                search.materialGroupId(),
                search.materialId(),
                searchTerm(search.supplierName()),
                PageRequest.of(safePage, safeSize, Sort.by("updatedAt").descending()));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDetail getDetail(Long id) {
        PurchaseOrder order = purchaseOrderRepository.findDetailedById(id)
                .orElseThrow(PurchaseOrderNotFoundException::new);
        List<PurchaseOrderDetail.Item> items = order.getItems().stream()
                .map(item -> new PurchaseOrderDetail.Item(
                        item, itemRepository.committedQuantity(item.getId()),
                        itemRepository.receivedQuantity(item.getId())))
                .toList();
        return new PurchaseOrderDetail(order, items);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getOwnedDraft(Long id, Long actorId) {
        PurchaseOrder order = purchaseOrderRepository.findDetailedById(id)
                .orElseThrow(PurchaseOrderNotFoundException::new);
        requireOwner(order, actorId);
        requireDraft(order);
        return order;
    }

    @Transactional(readOnly = true)
    public List<Material> activeMaterials() {
        return materialRepository.findByStatusOrderByMaterialCodeAsc(MaterialStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Material> allMaterials() {
        return materialRepository.findAll(Sort.by("materialCode").ascending());
    }

    @Transactional(readOnly = true)
    public List<MaterialGroup> allGroups() {
        return materialGroupRepository.findAllByOrderByCodeAsc();
    }

    @Transactional
    public PurchaseOrder createDraft(PurchaseOrderDraftCommand command, Long actorId) {
        if (command == null) {
            throw new InvalidPurchaseOrderException(null, "Dữ liệu đơn hàng là bắt buộc.");
        }
        String poCode = required(command.poCode(), "poCode", "Mã PO là bắt buộc.");
        validateCode(poCode);
        if (purchaseOrderRepository.existsByPoCode(poCode)) {
            throw new InvalidPurchaseOrderException("poCode", "Mã PO đã tồn tại.");
        }
        Header header = validateHeader(command);
        List<ItemDefinition> items = validateItems(command.items(), false);
        AppUser creator = appUserRepository.findById(actorId)
                .orElseThrow(PurchaseOrderNotFoundException::new);
        PurchaseOrder order = new PurchaseOrder(poCode, header.supplierName(), header.orderDate(), creator);
        order.replaceItems(items);
        try {
            return purchaseOrderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidPurchaseOrderException("poCode", "Mã PO đã tồn tại hoặc dữ liệu không hợp lệ.");
        }
    }

    @Transactional
    public PurchaseOrder updateDraft(Long id, PurchaseOrderDraftCommand command, Long actorId) {
        PurchaseOrder order = ownedLocked(id, actorId);
        requireDraft(order);
        Header header = validateHeader(command);
        List<ItemDefinition> items = validateItems(command.items(), false);
        order.updateDraft(header.supplierName(), header.orderDate());
        order.clearItems();
        purchaseOrderRepository.flush();
        order.addItems(items);
        try {
            return purchaseOrderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidPurchaseOrderException(null, "Dữ liệu đơn hàng không hợp lệ.");
        }
    }

    @Transactional
    public PurchaseOrder finalizeOrder(Long id, Long actorId) {
        PurchaseOrder order = ownedLocked(id, actorId);
        requireDraft(order);
        validateExistingItems(order.getItems());
        order.finalizeOrder();
        return purchaseOrderRepository.saveAndFlush(order);
    }

    @Transactional
    public PurchaseOrder cancel(Long id, Long actorId) {
        PurchaseOrder order = ownedLocked(id, actorId);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT
                && order.getStatus() != PurchaseOrderStatus.OPEN) {
            throw new PurchaseOrderConflictException(
                    "Chỉ có thể hủy đơn hàng đang lưu nháp hoặc đang mở.");
        }
        if (itemRepository.hasCommittedOrReceivedQuantity(id)) {
            throw new PurchaseOrderConflictException(
                    "Không thể hủy đơn hàng đã có số lượng cam kết hoặc đã nhận.");
        }
        order.cancel();
        return purchaseOrderRepository.saveAndFlush(order);
    }

    private PurchaseOrder ownedLocked(Long id, Long actorId) {
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(PurchaseOrderNotFoundException::new);
        requireOwner(order, actorId);
        return order;
    }

    private void requireOwner(PurchaseOrder order, Long actorId) {
        if (actorId == null || !actorId.equals(order.getCreatedBy().getId())) {
            throw new PurchaseOrderNotFoundException();
        }
    }

    private void requireDraft(PurchaseOrder order) {
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new PurchaseOrderConflictException("Đơn hàng không còn ở trạng thái lưu nháp.");
        }
    }

    private Header validateHeader(PurchaseOrderDraftCommand command) {
        if (command == null) {
            throw new InvalidPurchaseOrderException(null, "Dữ liệu đơn hàng là bắt buộc.");
        }
        String supplier = required(command.supplierName(), "supplierName", "Nhà cung cấp là bắt buộc.");
        if (supplier.length() > 300) {
            throw new InvalidPurchaseOrderException("supplierName", "Nhà cung cấp tối đa 300 ký tự.");
        }
        if (command.orderDate() == null) {
            throw new InvalidPurchaseOrderException("orderDate", "Ngày đặt hàng là bắt buộc.");
        }
        return new Header(supplier, command.orderDate());
    }

    private List<ItemDefinition> validateItems(List<PurchaseOrderDraftCommand.Item> submitted, boolean required) {
        List<IndexedItem> items = new ArrayList<>();
        List<PurchaseOrderDraftCommand.Item> safeItems = submitted == null ? List.of() : submitted;
        for (int index = 0; index < safeItems.size(); index++) {
            PurchaseOrderDraftCommand.Item item = safeItems.get(index);
            if (item != null && !item.isBlank()) {
                items.add(new IndexedItem(index, item));
            }
        }
        if (required && items.isEmpty()) {
            throw new InvalidPurchaseOrderException("items", "Cần ít nhất một vật tư để hoàn tất đơn hàng.");
        }
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> ids = items.stream()
                .map(indexed -> indexed.item().materialId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Material> materials = new HashMap<>();
        if (!ids.isEmpty()) {
            materialRepository.findDetailedByIdIn(ids)
                    .forEach(material -> materials.put(material.getId(), material));
        }
        Set<Long> seen = new HashSet<>();
        Long groupId = null;
        List<ItemDefinition> definitions = new ArrayList<>();

        for (IndexedItem indexed : items) {
            int index = indexed.index();
            PurchaseOrderDraftCommand.Item item = indexed.item();
            if (item.materialId() == null) {
                throw invalidItem(index, "materialId", "Vui lòng chọn vật tư.");
            }
            Material material = materials.get(item.materialId());
            if (material == null || material.getStatus() != MaterialStatus.ACTIVE) {
                throw invalidItem(index, "materialId", "Vật tư không tồn tại hoặc không hoạt động.");
            }
            if (!seen.add(material.getId())) {
                throw invalidItem(index, "materialId", "Vật tư bị trùng trong đơn hàng.");
            }
            if (groupId == null) {
                groupId = material.getMaterialGroup().getId();
            } else if (!groupId.equals(material.getMaterialGroup().getId())) {
                throw invalidItem(index, "materialId", "Tất cả vật tư phải thuộc cùng một nhóm.");
            }
            if (item.orderedQuantity() == null || item.orderedQuantity() <= 0) {
                throw invalidItem(index, "orderedQuantity", "Số lượng phải lớn hơn 0.");
            }
            validatePrice(index, material, item.unitPrice());
            definitions.add(new ItemDefinition(material, item.orderedQuantity(), item.unitPrice()));
        }
        return definitions;
    }

    private void validateExistingItems(List<PurchaseOrderItem> items) {
        if (items.isEmpty()) {
            throw new InvalidPurchaseOrderException("items", "Cần ít nhất một vật tư để hoàn tất đơn hàng.");
        }
        Long groupId = null;
        Set<Long> seen = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            PurchaseOrderItem item = items.get(index);
            Material material = item.getMaterial();
            if (material.getStatus() != MaterialStatus.ACTIVE) {
                throw invalidItem(index, "materialId", "Vật tư không còn hoạt động.");
            }
            if (!seen.add(material.getId())) {
                throw invalidItem(index, "materialId", "Vật tư bị trùng trong đơn hàng.");
            }
            if (groupId == null) {
                groupId = material.getMaterialGroup().getId();
            } else if (!groupId.equals(material.getMaterialGroup().getId())) {
                throw invalidItem(index, "materialId", "Tất cả vật tư phải thuộc cùng một nhóm.");
            }
            validatePrice(index, material, item.getUnitPrice());
        }
    }

    private void validatePrice(int index, Material material, BigDecimal unitPrice) {
        if (material.getMaterialGroup().isRequiresAccounting() && unitPrice == null) {
            throw invalidItem(index, "unitPrice", "Đơn giá là bắt buộc với nhóm cần hạch toán.");
        }
        if (unitPrice != null && unitPrice.signum() <= 0) {
            throw invalidItem(index, "unitPrice", "Đơn giá phải lớn hơn 0.");
        }
    }

    private InvalidPurchaseOrderException invalidItem(int index, String field, String message) {
        return new InvalidPurchaseOrderException("items[" + index + "]." + field, message);
    }

    private void validateCode(String code) {
        if (code.length() > 50) {
            throw new InvalidPurchaseOrderException("poCode", "Mã PO tối đa 50 ký tự.");
        }
    }

    private PurchaseOrderSearch normalizeSearch(PurchaseOrderSearch requested) {
        PurchaseOrderSearch search = requested == null ? defaultSearch() : requested;
        LocalDate from = search.createdFrom();
        LocalDate to = search.createdTo();
        if (from == null || to == null) {
            throw new InvalidPurchaseOrderException(null, "Từ ngày và đến ngày là bắt buộc.");
        }
        if (to.isBefore(from)) {
            throw new InvalidPurchaseOrderException(null, "Đến ngày phải lớn hơn hoặc bằng từ ngày.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 90) {
            throw new InvalidPurchaseOrderException(null, "Khoảng thời gian tìm kiếm tối đa 90 ngày.");
        }
        return search;
    }

    private String required(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidPurchaseOrderException(field, message);
        }
        return value.trim();
    }

    private String searchTerm(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private record Header(String supplierName, LocalDate orderDate) {
    }

    private record IndexedItem(int index, PurchaseOrderDraftCommand.Item item) {
    }
}
