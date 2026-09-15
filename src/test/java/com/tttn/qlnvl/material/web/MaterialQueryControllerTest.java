package com.tttn.qlnvl.material.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.tttn.qlnvl.material.application.MaterialService;
import com.tttn.qlnvl.shared.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MaterialQueryController.class)
@Import(SecurityConfig.class)
class MaterialQueryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MaterialService materialService;

    @Test
    void requesterCanRenderReadOnlyMaterialList() throws Exception {
        when(materialService.search(null, null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of()));
        when(materialService.allGroups()).thenReturn(List.of());

        mockMvc.perform(get("/materials").with(user("requester").roles("REQUESTER")))
                .andExpect(status().isOk())
                .andExpect(view().name("materials/list"));
    }

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/materials"))
                .andExpect(status().is3xxRedirection());
    }
}
