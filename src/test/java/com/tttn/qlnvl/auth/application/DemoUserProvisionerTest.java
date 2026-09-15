package com.tttn.qlnvl.auth.application;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.domain.Role;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DemoUserProvisionerTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsOneAccountForEachAcceptedBusinessRole() {
        when(appUserRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("local-demo-password")).thenReturn("bcrypt-hash");
        when(appUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new DemoUserProvisioner(appUserRepository, passwordEncoder, "local-demo-password").run(null);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository, org.mockito.Mockito.times(5)).save(captor.capture());
        List<AppUser> users = captor.getAllValues();

        assertThat(users).extracting(AppUser::getUsername).containsExactly(
                "requester_demo",
                "request_approver_demo",
                "inventory_staff_demo",
                "inventory_approver_demo",
                "warehouse_keeper_demo");
        assertThat(users).extracting(AppUser::getRole).containsExactly(Role.values());
        assertThat(users).extracting(AppUser::getPasswordHash).containsOnly("bcrypt-hash");
    }

    @Test
    void doesNotReplaceExistingAccounts() {
        AppUser existing = new AppUser("requester_demo", "existing-hash", "Existing", Role.REQUESTER);
        when(appUserRepository.findByUsername(any())).thenReturn(Optional.of(existing));

        new DemoUserProvisioner(appUserRepository, passwordEncoder, "local-demo-password").run(null);

        verify(appUserRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }
}
