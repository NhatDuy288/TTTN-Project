package com.tttn.qlnvl.warehousetransfer.web;

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
import com.tttn.qlnvl.warehousetransfer.application.TransferConfirmationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransferConfirmationController.class)
@Import(SecurityConfig.class)
class TransferConfirmationControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TransferConfirmationService confirmationService;

    @Test
    void warehouseKeeperCanOpenSourceConfirmationQueue() throws Exception {
        when(confirmationService.sourceQueue(0, 20)).thenReturn(Page.empty());

        mockMvc.perform(get("/transfer-confirmations/source")
                        .with(user("keeper").roles("WAREHOUSE_KEEPER")))
                .andExpect(status().isOk())
                .andExpect(view().name("transfer-confirmations/source-list"));
    }

    @Test
    void inventoryApproverCannotOpenSourceConfirmationQueue() throws Exception {
        mockMvc.perform(get("/transfer-confirmations/source")
                        .with(user("approver").roles("INVENTORY_APPROVER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void warehouseKeeperCanOpenDestinationConfirmationQueue() throws Exception {
        when(confirmationService.destinationQueue(0, 20)).thenReturn(Page.empty());

        mockMvc.perform(get("/transfer-confirmations/destination")
                        .with(user("keeper").roles("WAREHOUSE_KEEPER")))
                .andExpect(status().isOk())
                .andExpect(view().name("transfer-confirmations/destination-list"));
    }

    @Test
    void warehouseKeeperCanConfirmSourceMovement() throws Exception {
        mockMvc.perform(post("/transfer-confirmations/source/7/confirm")
                        .with(user(keeperPrincipal())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transfer-confirmations/source/7"));

        verify(confirmationService).confirmSource(7L, 99L);
    }

    @Test
    void sourceConfirmationRequiresCsrf() throws Exception {
        mockMvc.perform(post("/transfer-confirmations/source/7/confirm")
                        .with(user(keeperPrincipal())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        verify(confirmationService, never()).confirmSource(anyLong(), anyLong());
    }

    @Test
    void warehouseKeeperCanConfirmDestinationMovement() throws Exception {
        mockMvc.perform(post("/transfer-confirmations/destination/7/confirm")
                        .with(user(keeperPrincipal())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transfer-confirmations/destination/7"));

        verify(confirmationService).confirmDestination(7L, 99L);
    }

    @Test
    void destinationConfirmationRequiresCsrf() throws Exception {
        mockMvc.perform(post("/transfer-confirmations/destination/7/confirm")
                        .with(user(keeperPrincipal())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        verify(confirmationService, never()).confirmDestination(anyLong(), anyLong());
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
