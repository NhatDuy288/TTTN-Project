package com.tttn.qlnvl.warehousetransaction.web;

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
import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import com.tttn.qlnvl.warehouserequest.domain.Reason;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehousetransaction.application.WarehouseTransactionService;
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

@WebMvcTest(WarehouseTransactionController.class)
@Import(SecurityConfig.class)
class WarehouseTransactionControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private WarehouseTransactionService transactionService;

    @Test
    void inventoryStaffCanOpenDraftQueue() throws Exception {
        when(transactionService.queue(0, 20, WarehouseTransactionStatus.DRAFT))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/warehouse-transactions")
                        .with(user("staff").roles("INVENTORY_STAFF")))
                .andExpect(status().isOk())
                .andExpect(view().name("warehouse-transactions/list"));
    }

    @Test
    void requesterCannotOpenTransactionQueue() throws Exception {
        mockMvc.perform(get("/warehouse-transactions")
                        .with(user("requester").roles("REQUESTER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void inventoryStaffCanSubmitDraftTransaction() throws Exception {
        AppUserPrincipal principal = staffPrincipal();

        mockMvc.perform(post("/warehouse-transactions/7/submit")
                        .with(user(principal)).with(csrf())
                        .param("executionNote", "Đã kiểm tra"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/warehouse-transactions/7"));

        verify(transactionService).submit(7L, 99L, "Đã kiểm tra");
    }

    @Test
    void submitRequiresCsrf() throws Exception {
        mockMvc.perform(post("/warehouse-transactions/7/submit")
                        .with(user(staffPrincipal())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        verify(transactionService, never()).submit(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void noteLongerThanOneThousandCharactersRendersValidationError() throws Exception {
        WarehouseTransaction transaction = transactionDetail();
        when(transactionService.getDetail(7L)).thenReturn(transaction);

        mockMvc.perform(post("/warehouse-transactions/7/submit")
                        .with(user(staffPrincipal())).with(csrf())
                        .param("executionNote", "x".repeat(1001)))
                .andExpect(status().isOk())
                .andExpect(view().name("warehouse-transactions/detail"))
                .andExpect(model().attributeHasFieldErrors("submitForm", "executionNote"));

        verify(transactionService, never()).submit(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private WarehouseTransaction transactionDetail() {
        WarehouseTransaction transaction = mock(WarehouseTransaction.class);
        WarehouseRequest request = mock(WarehouseRequest.class);
        OperationType operationType = mock(OperationType.class);
        Reason reason = mock(Reason.class);
        AppUser creator = mock(AppUser.class);
        when(transaction.getId()).thenReturn(7L);
        when(transaction.getTransactionCode()).thenReturn("GRN_INP_20260917_001");
        when(transaction.getStatus()).thenReturn(WarehouseTransactionStatus.DRAFT);
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
