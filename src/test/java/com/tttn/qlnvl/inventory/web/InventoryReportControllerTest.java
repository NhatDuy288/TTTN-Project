package com.tttn.qlnvl.inventory.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.tttn.qlnvl.inventory.application.InventoryReportService;
import com.tttn.qlnvl.inventory.application.InventoryReportService.DetailedInventoryReport;
import com.tttn.qlnvl.inventory.application.NxtReportService;
import com.tttn.qlnvl.inventory.application.NxtReportService.MissingOpeningException;
import com.tttn.qlnvl.inventory.application.NxtReportService.NxtReport;
import com.tttn.qlnvl.shared.config.SecurityConfig;
import com.tttn.qlnvl.shared.web.BusinessDisplayFormatter;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryReportController.class)
@Import({SecurityConfig.class, BusinessDisplayFormatter.class})
class InventoryReportControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryReportService reportService;

    @MockitoBean
    private NxtReportService nxtReportService;

    @BeforeEach
    void setUp() {
        when(reportService.detailedInventory(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new DetailedInventoryReport(new PageImpl<>(List.of()), List.of()));
        when(reportService.activeWarehouses()).thenReturn(List.of());
        when(reportService.activeMaterialGroups()).thenReturn(List.of());
        when(reportService.activeMaterials()).thenReturn(List.of());
        when(nxtReportService.defaultFromDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(nxtReportService.defaultToDate()).thenReturn(LocalDate.of(2026, 9, 8));
        when(nxtReportService.warehouses()).thenReturn(List.of());
        when(nxtReportService.materialGroups()).thenReturn(List.of());
        when(nxtReportService.materials()).thenReturn(List.of());
    }

    @Test
    void inventoryStaffCanRenderDetailedInventoryWithFilters() throws Exception {
        mockMvc.perform(get("/reports/detailed-inventory")
                        .param("warehouseId", "11")
                        .param("materialGroupId", "12")
                        .param("materialId", "21")
                        .param("condition", "NEW", "OLD")
                        .param("size", "50")
                        .with(user("staff").roles("INVENTORY_STAFF")))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/detailed-inventory"))
                .andExpect(model().attribute("selectedSize", 50));

        verify(reportService).detailedInventory(11L, 12L, 21L,
                List.of(MaterialCondition.NEW, MaterialCondition.OLD), 0, 50);
    }

    @Test
    void inventoryApproverCanViewDetailedInventory() throws Exception {
        mockMvc.perform(get("/reports/detailed-inventory")
                        .with(user("approver").roles("INVENTORY_APPROVER")))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/detailed-inventory"));
    }

    @Test
    void warehouseKeeperCannotViewDetailedInventory() throws Exception {
        mockMvc.perform(get("/reports/detailed-inventory")
                        .with(user("keeper").roles("WAREHOUSE_KEEPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void warehouseKeeperCanViewNxtAndSubmitFilters() throws Exception {
        when(nxtReportService.report(eq(11L), eq(null), eq(null), any(),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 8)),
                eq(0), eq(20))).thenReturn(new NxtReport(new PageImpl<>(List.of()),
                        LocalDate.of(2026, 8, 31)));

        mockMvc.perform(get("/reports/nxt")
                        .param("warehouseId", "11")
                        .param("fromDate", "2026-09-01")
                        .param("toDate", "2026-09-08")
                        .with(user("keeper").roles("WAREHOUSE_KEEPER")))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/nxt"))
                .andExpect(model().attributeExists("nxtPage"))
                .andExpect(content().string(containsString("Kết quả Nhập")));
    }

    @Test
    void missingNxtOpeningStopsReportWith422() throws Exception {
        when(nxtReportService.report(eq(11L), eq(null), eq(null), any(),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 8)),
                eq(0), eq(20))).thenThrow(new MissingOpeningException(
                        LocalDate.of(2026, 8, 31)));

        mockMvc.perform(get("/reports/nxt")
                        .param("warehouseId", "11")
                        .param("fromDate", "2026-09-01")
                        .param("toDate", "2026-09-08")
                        .with(user("staff").roles("INVENTORY_STAFF")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(model().attributeExists("openingError"))
                .andExpect(model().attributeDoesNotExist("nxtPage"))
                .andExpect(content().string(containsString("Thiếu dữ liệu tồn đầu kỳ")));
    }

    @Test
    void requesterCannotViewNxt() throws Exception {
        mockMvc.perform(get("/reports/nxt")
                        .with(user("requester").roles("REQUESTER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/reports/detailed-inventory"))
                .andExpect(status().is3xxRedirection());
    }
}