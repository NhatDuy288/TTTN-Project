package com.tttn.qlnvl.integration;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.domain.Role;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialUnit;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

final class IntegrationFixtureFactory {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final AppUserRepository users;
    private final WarehouseRepository warehouses;
    private final MaterialGroupRepository groups;
    private final MaterialRepository materials;

    IntegrationFixtureFactory(AppUserRepository users, WarehouseRepository warehouses,
            MaterialGroupRepository groups, MaterialRepository materials) {
        this.users = users;
        this.warehouses = warehouses;
        this.groups = groups;
        this.materials = materials;
    }

    String nextKey(String scenario) {
        String prefix = scenario.replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
        return prefix + "%03d".formatted(SEQUENCE.incrementAndGet());
    }

    AppUser user(String key, String label, Role role, String passwordHash) {
        String username = ("it_" + key + "_" + label)
                .replaceAll("[^A-Za-z0-9_]", "")
                .toLowerCase(Locale.ROOT);
        return users.saveAndFlush(new AppUser(username, passwordHash,
                "Integration " + label + " " + key, role));
    }

    Warehouse warehouse(String key, String label, AppUser actor) {
        String normalizedLabel = label.replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
        return warehouses.saveAndFlush(new Warehouse("KHO_" + key + "_" + normalizedLabel,
                "Integration " + label + " " + key,
                "Integration test warehouse address", null, actor));
    }

    Material material(String key, String label) {
        return materials.saveAndFlush(new Material("IT_MAT_" + key,
                "Integration " + label + " " + key,
                groups.findByCode("CARD-VL-KHAC").orElseThrow(), MaterialUnit.CAI, null));
    }
}
