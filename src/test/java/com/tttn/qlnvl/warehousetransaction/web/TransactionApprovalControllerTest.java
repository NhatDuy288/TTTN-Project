package com.tttn.qlnvl.warehousetransaction.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.domain.Role;
import com.tttn.qlnvl.shared.config.SecurityConfig;
import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import com.tttn.qlnvl.warehouserequest.domain.Reason;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehousetransaction.application.TransactionApprovalService;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionApprovalController.class)
@Import(SecurityConfig.class)
class TransactionApprovalControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TransactionApprovalService approvalService;

    @Test
    void inventoryApproverCanOpenSubmittedQueue() throws Exception {
        when(approvalService.queue(0, 20)).thenReturn(Page.empty());

        mockMvc.perform(get("/transaction-approvals")
                        .with(user("approver").roles("INVENTORY_APPROVER")))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-approvals/list"));
    }

    @Test
    void inventoryStaffCannotOpenApprovalQueue() throws Exception {
        mockMvc.perform(get("/transaction-approvals")
                        .with(user("staff").roles("INVENTORY_STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void inventoryApproverCanApproveSubmittedTransaction() throws Exception {
        mockMvc.perform(post("/transaction-approvals/7/approve")
                        .with(user(approverPrincipal())).with(csrf())
                        .param("comment", "Đồng ý"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transaction-approvals/7"));

        verify(approvalService).approve(7L, 99L, "Đồng ý");
    }

    @Test
    void inventoryApproverCanRejectSubmittedTransaction() throws Exception {
        mockMvc.perform(post("/transaction-approvals/7/reject")
                        .with(user(approverPrincipal())).with(csrf())
                        .param("comment", "Không đạt"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transaction-approvals/7"));

        verify(approvalService).reject(7L, 99L, "Không đạt");
    }

    @Test
    void decisionRequiresCsrf() throws Exception {
        mockMvc.perform(post("/transaction-approvals/7/approve")
                        .with(user(approverPrincipal())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        verify(approvalService, never()).approve(anyLong(), anyLong(), anyString());
    }

    @Test
    void commentLongerThanFiveHundredCharactersRendersValidationError() throws Exception {
        WarehouseTransaction transaction = transactionDetail();
        when(approvalService.getDetail(7L)).thenReturn(transaction);

        mockMvc.perform(post("/transaction-approvals/7/approve")
                        .with(user(approverPrincipal())).with(csrf())
                        .param("comment", "x".repeat(501)))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-approvals/detail"))
                .andExpect(model().attributeHasFieldErrors("decisionForm", "comment"));

        verify(approvalService, never()).approve(anyLong(), anyLong(), anyString());
    }

    private WarehouseTransaction transactionDetail() {
        WarehouseTransaction transaction = mock(WarehouseTransaction.class);
        WarehouseRequest request = mock(WarehouseRequest.class);
        OperationType operationType = mock(OperationType.class);
        Reason reason = mock(Reason.class);
        AppUser creator = mock(AppUser.class);
        when(transaction.getId()).thenReturn(7L);
        when(transaction.getTransactionCode()).thenReturn("GRN_INP_20260917_001");
        when(transaction.getStatus()).thenReturn(WarehouseTransactionStatus.SUBMITTED);
        when(transaction.getRequest()).thenReturn(request);
        when(transaction.getDetails()).thenReturn(List.of());
        when(request.getRequestCode()).thenReturn("INP_20260917_001");
        when(request.getOperationType()).thenReturn(operationType);
        when(request.getReason()).thenReturn(reason);
        when(request.getCreatedBy()).thenReturn(creator);
        when(operationType.getCode()).thenReturn("INP");
        when(operationType.getName()).thenReturn("Nhập kho");
        when(reason.getCode()).thenReturn("IMPORT");
        when(reason.getName()).thenReturn("Nhập kho");
        when(creator.getUsername()).thenReturn("requester");
        when(creator.getFullName()).thenReturn("Người đề nghị");
        return transaction;
    }

    private AppUserPrincipal approverPrincipal() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(99L);
        when(user.getUsername()).thenReturn("approver");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getFullName()).thenReturn("Kế toán duyệt");
        when(user.getRole()).thenReturn(Role.INVENTORY_APPROVER);
        return AppUserPrincipal.from(user);
    }
}
