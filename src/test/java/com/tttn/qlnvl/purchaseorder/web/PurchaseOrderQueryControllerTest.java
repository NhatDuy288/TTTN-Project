package com.tttn.qlnvl.purchaseorder.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.tttn.qlnvl.purchaseorder.application.PurchaseOrderSearch;
import com.tttn.qlnvl.purchaseorder.application.PurchaseOrderService;
import com.tttn.qlnvl.shared.config.SecurityConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PurchaseOrderQueryController.class)
@Import(SecurityConfig.class)
class PurchaseOrderQueryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseOrderService purchaseOrderService;

    @Test
    void requesterCanRenderPurchaseOrderList() throws Exception {
        PurchaseOrderSearch defaults = new PurchaseOrderSearch(
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 15),
                null, null, null, null, null, null);
        when(purchaseOrderService.defaultSearch()).thenReturn(defaults);
        when(purchaseOrderService.search(any(PurchaseOrderSearch.class), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of()));
        when(purchaseOrderService.allGroups()).thenReturn(List.of());
        when(purchaseOrderService.allMaterials()).thenReturn(List.of());

        mockMvc.perform(get("/purchase-orders").with(user("requester").roles("REQUESTER")))
                .andExpect(status().isOk())
                .andExpect(view().name("purchase-orders/list"));
    }

    @Test
    void warehouseKeeperCannotViewPurchaseOrders() throws Exception {
        mockMvc.perform(get("/purchase-orders").with(user("keeper").roles("WAREHOUSE_KEEPER")))
                .andExpect(status().isForbidden());
    }
}
