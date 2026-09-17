package com.tttn.qlnvl.warehousetransaction.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.domain.Role;
import com.tttn.qlnvl.shared.config.SecurityConfig;
import com.tttn.qlnvl.warehousetransaction.application.TransactionConfirmationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionConfirmationController.class)
@Import(SecurityConfig.class)
class TransactionConfirmationControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TransactionConfirmationService confirmationService;

    @Test
    void warehouseKeeperCanOpenConfirmationQueue() throws Exception {
        when(confirmationService.queue(0, 20)).thenReturn(Page.empty());

        mockMvc.perform(get("/transaction-confirmations")
                        .with(user("keeper").roles("WAREHOUSE_KEEPER")))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-confirmations/list"));
    }

    @Test
    void inventoryApproverCannotOpenConfirmationQueue() throws Exception {
        mockMvc.perform(get("/transaction-confirmations")
                        .with(user("approver").roles("INVENTORY_APPROVER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void warehouseKeeperCanConfirmApprovedMovement() throws Exception {
        mockMvc.perform(post("/transaction-confirmations/7/confirm")
                        .with(user(keeperPrincipal())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transaction-confirmations/7"));

        verify(confirmationService).confirm(7L, 99L);
    }

    @Test
    void confirmationRequiresCsrf() throws Exception {
        mockMvc.perform(post("/transaction-confirmations/7/confirm")
                        .with(user(keeperPrincipal())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        verify(confirmationService, never()).confirm(anyLong(), anyLong());
    }

    private AppUserPrincipal keeperPrincipal() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(99L);
        when(user.getUsername()).thenReturn("keeper");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getFullName()).thenReturn("Thủ kho");
        when(user.getRole()).thenReturn(Role.WAREHOUSE_KEEPER);
        return AppUserPrincipal.from(user);
    }
}
