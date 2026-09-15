package com.tttn.qlnvl.warehouse.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.tttn.qlnvl.shared.config.SecurityConfig;
import com.tttn.qlnvl.warehouse.application.WarehouseService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WarehouseQueryController.class)
@Import(SecurityConfig.class)
class WarehouseQueryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarehouseService warehouseService;

    @Test
    void inventoryApproverCanRenderReadOnlyWarehouseList() throws Exception {
        when(warehouseService.search(null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/warehouses").with(user("approver").roles("INVENTORY_APPROVER")))
                .andExpect(status().isOk())
                .andExpect(view().name("warehouses/list"));
    }
}
