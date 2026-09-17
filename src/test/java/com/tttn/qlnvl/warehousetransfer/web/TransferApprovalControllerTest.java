package com.tttn.qlnvl.warehousetransfer.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import com.tttn.qlnvl.warehouserequest.domain.Reason;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehousetransfer.application.TransferApprovalService;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransfer;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransferApprovalController.class)
@Import(SecurityConfig.class)
class TransferApprovalControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TransferApprovalService approvalService;

    @Test
    void inventoryApproverCanOpenSubmittedQueue() throws Exception {
        when(approvalService.queue(0, 20)).thenReturn(Page.empty());

        mockMvc.perform(get("/transfer-approvals")
                        .with(user("approver").roles("INVENTORY_APPROVER")))
                .andExpect(status().isOk())
                .andExpect(view().name("transfer-approvals/list"));
    }

    @Test
    void inventoryStaffCannotOpenApprovalQueue() throws Exception {
        mockMvc.perform(get("/transfer-approvals")
                        .with(user("staff").roles("INVENTORY_STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void inventoryApproverCanApproveSubmittedTransfer() throws Exception {
        mockMvc.perform(post("/transfer-approvals/7/approve")
                        .with(user(approverPrincipal())).with(csrf())
                        .param("comment", "Đồng ý"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transfer-approvals/7"));

        verify(approvalService).approve(7L, 99L, "Đồng ý");
    }

    @Test
    void inventoryApproverCanRejectSubmittedTransfer() throws Exception {
        mockMvc.perform(post("/transfer-approvals/7/reject")
                        .with(user(approverPrincipal())).with(csrf())
                        .param("comment", "Không đạt"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transfer-approvals/7"));

        verify(approvalService).reject(7L, 99L, "Không đạt");
    }

    @Test
    void decisionRequiresCsrf() throws Exception {
        mockMvc.perform(post("/transfer-approvals/7/approve")
                        .with(user(approverPrincipal())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        verify(approvalService, never()).approve(anyLong(), anyLong(), anyString());
    }

    @Test
    void commentLongerThanFiveHundredCharactersRendersValidationError() throws Exception {
        WarehouseTransfer transfer = transferDetail();
        when(approvalService.getDetail(7L)).thenReturn(transfer);

        mockMvc.perform(post("/transfer-approvals/7/approve")
                        .with(user(approverPrincipal())).with(csrf())
                        .param("comment", "x".repeat(501)))
                .andExpect(status().isOk())
                .andExpect(view().name("transfer-approvals/detail"))
                .andExpect(model().attributeHasFieldErrors("decisionForm", "comment"));

        verify(approvalService, never()).approve(anyLong(), anyLong(), anyString());
    }

    private WarehouseTransfer transferDetail() {
        WarehouseTransfer transfer = mock(WarehouseTransfer.class);
        WarehouseRequest request = mock(WarehouseRequest.class);
        OperationType operationType = mock(OperationType.class);
        Reason reason = mock(Reason.class);
        AppUser creator = mock(AppUser.class);
        Warehouse source = mock(Warehouse.class);
        Warehouse destination = mock(Warehouse.class);
        when(transfer.getId()).thenReturn(7L);
        when(transfer.getStatus()).thenReturn(WarehouseTransferStatus.SUBMITTED);
        when(transfer.getRequest()).thenReturn(request);
        when(transfer.getSourceWarehouse()).thenReturn(source);
        when(transfer.getDestinationWarehouse()).thenReturn(destination);
        when(transfer.getDetails()).thenReturn(List.of());
        when(request.getRequestCode()).thenReturn("TRF_20260917_001");
        when(request.getOperationType()).thenReturn(operationType);
        when(request.getReason()).thenReturn(reason);
        when(request.getCreatedBy()).thenReturn(creator);
        when(operationType.getCode()).thenReturn("TRF");
        when(operationType.getName()).thenReturn("Điều chuyển");
        when(reason.getCode()).thenReturn("TRANSFER");
        when(reason.getName()).thenReturn("Điều chuyển kho");
        when(creator.getUsername()).thenReturn("requester");
        when(creator.getFullName()).thenReturn("Người đề nghị");
        when(source.getWarehouseCode()).thenReturn("KHO_A_01");
        when(source.getWarehouseName()).thenReturn("Kho A");
        when(destination.getWarehouseCode()).thenReturn("KHO_B_01");
        when(destination.getWarehouseName()).thenReturn("Kho B");
        return transfer;
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
