package com.tttn.qlnvl.auth.application;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.domain.Role;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoUserProvisioner implements ApplicationRunner {

    private static final List<DemoUserDefinition> DEMO_USERS = List.of(
            new DemoUserDefinition("requester_demo", "Người đề nghị Demo", Role.REQUESTER),
            new DemoUserDefinition("request_approver_demo", "Người duyệt đề nghị Demo", Role.REQUEST_APPROVER),
            new DemoUserDefinition("inventory_staff_demo", "Nhân viên kho Demo", Role.INVENTORY_STAFF),
            new DemoUserDefinition("inventory_approver_demo", "Người duyệt kho Demo", Role.INVENTORY_APPROVER),
            new DemoUserDefinition("warehouse_keeper_demo", "Thủ kho Demo", Role.WAREHOUSE_KEEPER));

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String rawPassword;

    public DemoUserProvisioner(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.password}") String rawPassword) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.rawPassword = rawPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (rawPassword.isBlank()) {
            throw new IllegalStateException("DEMO_USER_PASSWORD must not be blank");
        }

        DEMO_USERS.forEach(definition -> appUserRepository.findByUsername(definition.username())
                .orElseGet(() -> appUserRepository.save(new AppUser(
                        definition.username(),
                        passwordEncoder.encode(rawPassword),
                        definition.fullName(),
                        definition.role()))));
    }

    private record DemoUserDefinition(String username, String fullName, Role role) {
    }
}
