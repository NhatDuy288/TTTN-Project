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
import com.tttn.qlnvl.warehousetransfer.application.WarehouseTransferService;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WarehouseTransferController.class)
@Import(SecurityConfig.class)
class WarehouseTransferControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private WarehouseTransferService transferService;

    @Test
    void inventoryStaffCanOpenDraftTransferQueue() throws Exception {
        when(transferService.queue(0, 20, WarehouseTransferStatus.DRAFT))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/warehouse-transfers")
                        .with(user("staff").roles("INVENTORY_STAFF")))
                .andExpect(status().isOk())
                .andExpect(view().name("warehouse-transfers/list"));
    }

    @Test
    void requesterCannotOpenTransferQueue() throws Exception {
        mockMvc.perform(get("/warehouse-transfers")
                        .with(user("requester").roles("REQUESTER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void inventoryStaffCanSubmitDraftTransfer() throws Exception {
        mockMvc.perform(post("/warehouse-transfers/7/submit")
                        .with(user(staffPrincipal())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/warehouse-transfers/7"));

        verify(transferService).submit(7L, 99L);
    }

    @Test
    void submitRequiresCsrf() throws Exception {
        mockMvc.perform(post("/warehouse-transfers/7/submit")
                        .with(user(staffPrincipal())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        verify(transferService, never()).submit(anyLong(), anyLong());
    }

    private AppUserPrincipal staffPrincipal() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(99L);
        when(user.getUsername()).thenReturn("staff");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getFullName()).thenReturn("Nhân viên kho");
        when(user.getRole()).thenReturn(Role.INVENTORY_STAFF);
        return AppUserPrincipal.from(user);
    }
}
