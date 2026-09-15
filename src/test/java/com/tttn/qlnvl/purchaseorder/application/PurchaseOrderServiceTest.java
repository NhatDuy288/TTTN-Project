package com.tttn.qlnvl.purchaseorder.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.domain.MaterialStatus;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderStatus;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderItemRepository;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PurchaseOrderServiceTest {
    private PurchaseOrderRepository orderRepository;
    private PurchaseOrderItemRepository itemRepository;
    private MaterialRepository materialRepository;
    private AppUserRepository appUserRepository;
    private PurchaseOrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(PurchaseOrderRepository.class);
        itemRepository = mock(PurchaseOrderItemRepository.class);
        materialRepository = mock(MaterialRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        service = new PurchaseOrderService(orderRepository, itemRepository, materialRepository,
                mock(MaterialGroupRepository.class), appUserRepository);
    }

    @Test
    void createDraftAllowsNoItems() {
        AppUser creator = mock(AppUser.class);
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(creator));
        when(orderRepository.saveAndFlush(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder order = service.createDraft(command("PO-001", List.of()), 7L);

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(order.getItems()).isEmpty();
        assertThat(order.getPoCode()).isEqualTo("PO-001");
    }

    @Test
    void accountingMaterialRequiresUnitPrice() {
        Material material = material(3L, 9L, true);
        when(materialRepository.findDetailedByIdIn(List.of(3L))).thenReturn(List.of(material));

        assertThatThrownBy(() -> service.createDraft(command("PO-002", List.of(
                new PurchaseOrderDraftCommand.Item(3L, 10L, null))), 7L))
                .isInstanceOf(InvalidPurchaseOrderException.class)
                .hasMessageContaining("Đơn giá");
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void itemsFromDifferentGroupsAreRejected() {
        Material first = material(3L, 9L, false);
        Material second = material(4L, 10L, false);
        when(materialRepository.findDetailedByIdIn(List.of(3L, 4L))).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.createDraft(command("PO-003", List.of(
                new PurchaseOrderDraftCommand.Item(3L, 10L, null),
                new PurchaseOrderDraftCommand.Item(4L, 5L, null))), 7L))
                .isInstanceOf(InvalidPurchaseOrderException.class)
                .hasMessageContaining("cùng một nhóm");
    }

    @Test
    void finalizeRequiresAtLeastOneItem() {
        PurchaseOrder order = ownedOrder(7L, PurchaseOrderStatus.DRAFT, List.of());
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.finalizeOrder(11L, 7L))
                .isInstanceOf(InvalidPurchaseOrderException.class)
                .hasMessageContaining("ít nhất một");
    }

    @Test
    void cancelRejectsOrderWithCommittedQuantity() {
        PurchaseOrder order = ownedOrder(7L, PurchaseOrderStatus.OPEN, List.of());
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(order));
        when(itemRepository.hasCommittedOrReceivedQuantity(11L)).thenReturn(true);

        assertThatThrownBy(() -> service.cancel(11L, 7L))
                .isInstanceOf(PurchaseOrderConflictException.class)
                .hasMessageContaining("cam kết");
        verify(orderRepository, never()).saveAndFlush(order);
    }

    @Test
    void updateFlushesRemovedDraftItemsBeforeAddingReplacementRows() {
        AppUser creator = mock(AppUser.class);
        when(creator.getId()).thenReturn(7L);
        Material material = material(3L, 9L, true);
        PurchaseOrder order = new PurchaseOrder(
                "PO-004", "Nhà cung cấp cũ", LocalDate.of(2026, 9, 14), creator);
        order.replaceItems(List.of(new PurchaseOrder.ItemDefinition(
                material, 10L, new BigDecimal("1200.0000"))));
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(order));
        when(materialRepository.findDetailedByIdIn(List.of(3L))).thenReturn(List.of(material));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        PurchaseOrder updated = service.updateDraft(11L, command("PO-004", List.of(
                new PurchaseOrderDraftCommand.Item(3L, 12L, new BigDecimal("1300.7500")))), 7L);

        assertThat(updated.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getOrderedQuantity()).isEqualTo(12L);
                    assertThat(item.getUnitPrice()).isEqualByComparingTo("1300.7500");
                });
        InOrder writes = org.mockito.Mockito.inOrder(orderRepository);
        writes.verify(orderRepository).flush();
        writes.verify(orderRepository).saveAndFlush(order);
    }

    @Test
    void searchRejectsRangeLongerThanNinetyDays() {
        PurchaseOrderSearch search = new PurchaseOrderSearch(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 2),
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.search(search, 0, 20))
                .isInstanceOf(InvalidPurchaseOrderException.class)
                .hasMessageContaining("90 ngày");
    }

    private PurchaseOrderDraftCommand command(String code, List<PurchaseOrderDraftCommand.Item> items) {
        return new PurchaseOrderDraftCommand(code, "Nhà cung cấp A", LocalDate.of(2026, 9, 15), items);
    }

    private Material material(Long id, Long groupId, boolean accounting) {
        MaterialGroup group = mock(MaterialGroup.class);
        when(group.getId()).thenReturn(groupId);
        when(group.isRequiresAccounting()).thenReturn(accounting);
        Material material = mock(Material.class);
        when(material.getId()).thenReturn(id);
        when(material.getStatus()).thenReturn(MaterialStatus.ACTIVE);
        when(material.getMaterialGroup()).thenReturn(group);
        return material;
    }

    private PurchaseOrder ownedOrder(Long creatorId, PurchaseOrderStatus status,
                                     List<com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem> items) {
        AppUser creator = mock(AppUser.class);
        when(creator.getId()).thenReturn(creatorId);
        PurchaseOrder order = mock(PurchaseOrder.class);
        when(order.getCreatedBy()).thenReturn(creator);
        when(order.getStatus()).thenReturn(status);
        when(order.getItems()).thenReturn(items);
        return order;
    }
}
