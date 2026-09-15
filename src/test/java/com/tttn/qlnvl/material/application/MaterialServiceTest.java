package com.tttn.qlnvl.material.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.domain.MaterialStatus;
import com.tttn.qlnvl.material.domain.MaterialUnit;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.shared.application.InvalidMasterDataException;
import com.tttn.qlnvl.shared.application.MasterDataLifecycleConflictException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaterialServiceTest {
    private MaterialRepository materialRepository;
    private MaterialGroupRepository groupRepository;
    private MaterialService service;

    @BeforeEach
    void setUp() {
        materialRepository = mock(MaterialRepository.class);
        groupRepository = mock(MaterialGroupRepository.class);
        service = new MaterialService(materialRepository, groupRepository);
    }

    @Test
    void createRequiresGlCodeForAccountingGroup() {
        MaterialGroup group = mock(MaterialGroup.class);
        when(group.isActive()).thenReturn(true);
        when(group.isRequiresAccounting()).thenReturn(true);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.create(1L, "CARD_001", "Phôi thẻ demo", MaterialUnit.CAI, null))
                .isInstanceOf(InvalidMasterDataException.class)
                .hasMessageContaining("GL Code");
        verify(materialRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateKeepsCodeAndGroupImmutable() {
        MaterialGroup group = mock(MaterialGroup.class);
        when(group.isRequiresAccounting()).thenReturn(false);
        Material material = new Material("MAT_001", "Tên cũ", group, MaterialUnit.CAI, null);
        when(materialRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(material));
        when(materialRepository.saveAndFlush(material)).thenReturn(material);

        Material updated = service.update(9L, "Tên mới", MaterialUnit.HOP, null, MaterialStatus.ACTIVE);

        assertThat(updated.getMaterialCode()).isEqualTo("MAT_001");
        assertThat(updated.getMaterialGroup()).isSameAs(group);
        assertThat(updated.getMaterialName()).isEqualTo("Tên mới");
        assertThat(updated.getUnit()).isEqualTo(MaterialUnit.HOP);
    }

    @Test
    void deactivateIsRejectedWhenMaterialStillHasStock() {
        MaterialGroup group = mock(MaterialGroup.class);
        Material material = new Material("MAT_001", "Vật tư", group, MaterialUnit.CAI, null);
        when(materialRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(material));
        when(materialRepository.hasOnHandStock(9L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(
                9L, "Vật tư", MaterialUnit.CAI, null, MaterialStatus.INACTIVE))
                .isInstanceOf(MasterDataLifecycleConflictException.class);
        verify(materialRepository, never()).saveAndFlush(material);
    }
}
