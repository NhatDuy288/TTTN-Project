package com.tttn.qlnvl.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.domain.Role;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Test
    void loadsExactlyOneAcceptedBusinessRole() {
        AppUser appUser = new AppUser(
                "warehouse.keeper",
                "$2a$10$encoded",
                "Warehouse Keeper",
                Role.WAREHOUSE_KEEPER);
        when(appUserRepository.findByUsername("warehouse.keeper")).thenReturn(Optional.of(appUser));

        AppUserPrincipal principal = (AppUserPrincipal) new DatabaseUserDetailsService(appUserRepository)
                .loadUserByUsername("warehouse.keeper");

        assertThat(principal.getFullName()).isEqualTo("Warehouse Keeper");
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_WAREHOUSE_KEEPER");
    }

    @Test
    void missingUsernameUsesGenericAuthenticationFailure() {
        when(appUserRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DatabaseUserDetailsService(appUserRepository)
                .loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Tên đăng nhập hoặc mật khẩu không đúng.");
    }
}
