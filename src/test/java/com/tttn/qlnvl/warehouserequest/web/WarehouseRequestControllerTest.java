package com.tttn.qlnvl.warehouserequest.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestDraftCommand;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestFormOptions;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestOptionService;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestService;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WarehouseRequestController.class)
@Import(SecurityConfig.class)
class WarehouseRequestControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private WarehouseRequestService requestService;
    @MockitoBean private WarehouseRequestOptionService optionService;

    @BeforeEach
    void setUp() {
        when(optionService.load()).thenReturn(new WarehouseRequestFormOptions(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    void requesterCanRenderCreateForm() throws Exception {
        mockMvc.perform(get("/requests/new").with(user("requester").roles("REQUESTER")))
                .andExpect(status().isOk())
                .andExpect(view().name("requests/form"));
    }

    @Test
    void requesterCanListOnlyOwnedRequests() throws Exception {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(99L);
        when(user.getUsername()).thenReturn("requester");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getFullName()).thenReturn("Người đề nghị");
        when(user.getRole()).thenReturn(Role.REQUESTER);
        AppUserPrincipal principal = AppUserPrincipal.from(user);
        when(requestService.searchOwned(99L, "OUT", WarehouseRequestStatus.SUBMITTED, 0, 20))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/requests").with(user(principal))
                        .param("keyword", "OUT")
                        .param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(view().name("requests/list"));

        verify(requestService).searchOwned(99L, "OUT", WarehouseRequestStatus.SUBMITTED, 0, 20);
    }

    @Test
    void nonRequesterCannotListRequests() throws Exception {
        mockMvc.perform(get("/requests").with(user("keeper").roles("WAREHOUSE_KEEPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void requesterCanCreateDraft() throws Exception {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(99L);
        when(user.getUsername()).thenReturn("requester");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getFullName()).thenReturn("Người đề nghị");
        when(user.getRole()).thenReturn(Role.REQUESTER);
        AppUserPrincipal principal = AppUserPrincipal.from(user);
        WarehouseRequest request = mock(WarehouseRequest.class);
        when(request.getId()).thenReturn(5L);
        when(requestService.createDraft(any(WarehouseRequestDraftCommand.class), any()))
                .thenReturn(request);

        mockMvc.perform(post("/requests").with(user(principal)).with(csrf())
                        .param("operationTypeId", "1")
                        .param("reasonId", "2")
                        .param("destinationWarehouseId", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/requests/5"));

        verify(requestService).createDraft(any(WarehouseRequestDraftCommand.class), any());
    }

    @Test
    void warehouseKeeperCannotRenderCreateForm() throws Exception {
        mockMvc.perform(get("/requests/new").with(user("keeper").roles("WAREHOUSE_KEEPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void requesterCanSubmitOwnedDraft() throws Exception {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(99L);
        when(user.getUsername()).thenReturn("requester");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getFullName()).thenReturn("Người đề nghị");
        when(user.getRole()).thenReturn(Role.REQUESTER);
        AppUserPrincipal principal = AppUserPrincipal.from(user);

        mockMvc.perform(post("/requests/5/submit").with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/requests/5"));

        verify(requestService).submit(5L, 99L);
    }

    @Test
    void requesterCanCancelOwnedDraftOrSubmittedRequest() throws Exception {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(99L);
        when(user.getUsername()).thenReturn("requester");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getFullName()).thenReturn("Người đề nghị");
        when(user.getRole()).thenReturn(Role.REQUESTER);
        AppUserPrincipal principal = AppUserPrincipal.from(user);

        mockMvc.perform(post("/requests/5/cancel").with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/requests/5"));

        verify(requestService).cancel(5L, 99L);
    }
}
