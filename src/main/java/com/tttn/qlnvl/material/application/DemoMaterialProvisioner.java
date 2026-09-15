package com.tttn.qlnvl.material.application;

import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.domain.MaterialUnit;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoMaterialProvisioner implements ApplicationRunner {
    private static final List<DemoMaterialDefinition> DEMO_MATERIALS = List.of(
            new DemoMaterialDefinition(
                    "CARD-PHOI", "CARD_BLANK_DEMO", "Phôi thẻ demo",
                    MaterialUnit.CAI, "GL-DEMO-CARD"),
            new DemoMaterialDefinition(
                    "CARD-VL-KHAC", "CARD_OTHER_DEMO", "Vật tư thẻ khác demo",
                    MaterialUnit.CAI, null),
            new DemoMaterialDefinition(
                    "POS-THIETBI", "POS_DEVICE_DEMO", "Thiết bị POS demo",
                    MaterialUnit.MAY, null),
            new DemoMaterialDefinition(
                    "POS-VL-KHAC", "POS_OTHER_DEMO", "Vật tư POS khác demo",
                    MaterialUnit.CAI, null),
            new DemoMaterialDefinition(
                    "ATM-VL-KHAC", "ATM_OTHER_DEMO", "Vật tư ATM khác demo",
                    MaterialUnit.CAI, null));

    private final MaterialRepository materialRepository;
    private final MaterialGroupRepository materialGroupRepository;

    public DemoMaterialProvisioner(
            MaterialRepository materialRepository,
            MaterialGroupRepository materialGroupRepository) {
        this.materialRepository = materialRepository;
        this.materialGroupRepository = materialGroupRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        for (DemoMaterialDefinition definition : DEMO_MATERIALS) {
            if (materialRepository.existsByMaterialCode(definition.materialCode())) {
                continue;
            }
            MaterialGroup group = materialGroupRepository.findByCode(definition.groupCode())
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing accepted material group: " + definition.groupCode()));
            materialRepository.save(new Material(
                    definition.materialCode(),
                    definition.materialName(),
                    group,
                    definition.unit(),
                    definition.glCode()));
        }
    }

    private record DemoMaterialDefinition(
            String groupCode,
            String materialCode,
            String materialName,
            MaterialUnit unit,
            String glCode) {
    }
}
