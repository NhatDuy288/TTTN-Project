package com.tttn.qlnvl.warehouserequest.web;

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
import com.tttn.qlnvl.warehouserequest.application.RequestApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RequestApprovalController.class)
@Import(SecurityConfig.class)
class RequestApprovalControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private RequestApprovalService approvalService;

    @Test
    void approverCanOpenQueue() throws Exception {
        when(approvalService.queue(0, 20)).thenReturn(Page.empty());

        mockMvc.perform(get("/request-approvals")
                        .with(user("approver").roles("REQUEST_APPROVER")))
                .andExpect(status().isOk())
                .andExpect(view().name("request-approvals/list"));
    }

    @Test
    void requesterCannotOpenApprovalQueue() throws Exception {
        mockMvc.perform(get("/request-approvals").with(user("requester").roles("REQUESTER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void approverCanApproveSubmittedRequest() throws Exception {
        AppUserPrincipal principal = approverPrincipal();

        mockMvc.perform(post("/request-approvals/7/approve").with(user(principal)).with(csrf())
                        .param("comment", "Đồng ý"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/request-approvals/7"));

        verify(approvalService).approve(7L, 99L, "Đồng ý");
    }

    @Test
    void approverCanRejectSubmittedRequest() throws Exception {
        AppUserPrincipal principal = approverPrincipal();

        mockMvc.perform(post("/request-approvals/7/reject").with(user(principal)).with(csrf())
                        .param("comment", "Từ chối"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/request-approvals/7"));

        verify(approvalService).reject(7L, 99L, "Từ chối");
    }

    private AppUserPrincipal approverPrincipal() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(99L);
        when(user.getUsername()).thenReturn("approver");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getFullName()).thenReturn("Người duyệt");
        when(user.getRole()).thenReturn(Role.REQUEST_APPROVER);
        return AppUserPrincipal.from(user);
    }
}
