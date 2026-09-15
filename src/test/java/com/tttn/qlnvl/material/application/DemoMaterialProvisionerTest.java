package com.tttn.qlnvl.material.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoMaterialProvisionerTest {
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private MaterialGroupRepository materialGroupRepository;

    @Test
    void createsOneDemoMaterialPerAcceptedGroup() {
        MaterialGroup group = org.mockito.Mockito.mock(MaterialGroup.class);
        when(materialGroupRepository.findByCode(any())).thenReturn(Optional.of(group));
        when(materialRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new DemoMaterialProvisioner(materialRepository, materialGroupRepository).run(null);

        ArgumentCaptor<Material> captor = ArgumentCaptor.forClass(Material.class);
        verify(materialRepository, org.mockito.Mockito.times(5)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Material::getMaterialCode).containsExactly(
                "CARD_BLANK_DEMO",
                "CARD_OTHER_DEMO",
                "POS_DEVICE_DEMO",
                "POS_OTHER_DEMO",
                "ATM_OTHER_DEMO");
        assertThat(captor.getAllValues()).filteredOn(material -> material.getGlCode() != null)
                .singleElement()
                .extracting(Material::getGlCode)
                .isEqualTo("GL-DEMO-CARD");
    }

    @Test
    void restartDoesNotCreateDuplicateDemoMaterials() {
        when(materialRepository.existsByMaterialCode(any())).thenReturn(true);

        new DemoMaterialProvisioner(materialRepository, materialGroupRepository).run(null);

        verify(materialRepository, never()).save(any());
        verify(materialGroupRepository, never()).findByCode(any());
    }
}
