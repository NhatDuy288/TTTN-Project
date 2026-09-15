package com.tttn.qlnvl.warehouse.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.domain.Role;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.shared.application.MasterDataLifecycleConflictException;
import com.tttn.qlnvl.shared.application.InvalidMasterDataException;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.domain.WarehouseStatus;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarehouseServiceTest {
    private WarehouseRepository warehouseRepository;
    private AppUserRepository appUserRepository;
    private WarehouseService service;

    @BeforeEach
    void setUp() {
        warehouseRepository = mock(WarehouseRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        service = new WarehouseService(warehouseRepository, appUserRepository);
    }

    @Test
    void updateKeepsCodeAndStoresLatestOperator() {
        AppUser oldActor = new AppUser("old", "hash", "Thủ kho cũ", Role.WAREHOUSE_KEEPER);
        AppUser newActor = new AppUser("new", "hash", "Thủ kho mới", Role.WAREHOUSE_KEEPER);
        Warehouse warehouse = new Warehouse(
                "KHO_01", "Kho ban đầu", "Địa chỉ kho ban đầu đủ dài", null, oldActor);
        when(warehouseRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(warehouse));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(newActor));
        when(warehouseRepository.saveAndFlush(warehouse)).thenReturn(warehouse);

        Warehouse updated = service.update(3L, "Kho đã cập nhật",
                "Địa chỉ kho đã cập nhật đủ dài", "Ghi chú", WarehouseStatus.ACTIVE, 7L);

        assertThat(updated.getWarehouseCode()).isEqualTo("KHO_01");
        assertThat(updated.getUpdatedBy()).isSameAs(newActor);
        assertThat(updated.getWarehouseName()).isEqualTo("Kho đã cập nhật");
    }

    @Test
    void deactivateIsRejectedWhenWarehouseHasActiveWorkflow() {
        AppUser actor = new AppUser("keeper", "hash", "Thủ kho", Role.WAREHOUSE_KEEPER);
        Warehouse warehouse = new Warehouse(
                "KHO_01", "Kho nghiệp vụ", "Địa chỉ kho nghiệp vụ đủ dài", null, actor);
        when(warehouseRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.hasOnHandStock(3L)).thenReturn(false);
        when(warehouseRepository.hasActiveWorkflow(3L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(3L, "Kho nghiệp vụ",
                "Địa chỉ kho nghiệp vụ đủ dài", null, WarehouseStatus.INACTIVE, 7L))
                .isInstanceOf(MasterDataLifecycleConflictException.class);
        verify(appUserRepository, never()).findById(7L);
        verify(warehouseRepository, never()).saveAndFlush(warehouse);
    }

    @Test
    void createValidatesLengthsAfterTrimming() {
        assertThatThrownBy(() -> service.create(
                "KHO_01", "  abc  ", "Địa chỉ kho hợp lệ và đủ dài", null, 7L))
                .isInstanceOf(InvalidMasterDataException.class)
                .hasMessageContaining("5 đến 50");
        verify(warehouseRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }
}
