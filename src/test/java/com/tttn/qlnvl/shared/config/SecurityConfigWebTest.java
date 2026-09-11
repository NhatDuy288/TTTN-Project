package com.tttn.qlnvl.shared.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.tttn.qlnvl.auth.web.AuthController;
import com.tttn.qlnvl.shared.web.DashboardController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, DashboardController.class})
@Import(SecurityConfig.class)
class SecurityConfigWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đăng nhập hệ thống")));
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void authenticatedBusinessRoleCanOpenSharedDashboard() throws Exception {
        mockMvc.perform(get("/dashboard").with(user("requester").roles("REQUESTER")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("role", "REQUESTER"));
    }

    @Test
    void requesterCannotOpenRequestApprovalQueue() throws Exception {
        mockMvc.perform(get("/request-approvals").with(user("requester").roles("REQUESTER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void warehouseKeeperCannotOpenDetailedInventoryReport() throws Exception {
        mockMvc.perform(get("/reports/detailed-inventory")
                        .with(user("keeper").roles("WAREHOUSE_KEEPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void inventoryStaffPassesRoleCheckForMaterialCreatePage() throws Exception {
        mockMvc.perform(get("/materials/new")
                        .with(user("inventory").roles("INVENTORY_STAFF")))
                .andExpect(status().isNotFound());
    }

    @Test
    void logoutRequiresCsrf() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(user("requester").roles("REQUESTER"))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesAuthenticatedSession() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(user("requester").roles("REQUESTER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }
}
