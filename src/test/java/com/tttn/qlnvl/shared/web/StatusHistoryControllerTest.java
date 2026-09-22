package com.tttn.qlnvl.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.domain.Role;
import com.tttn.qlnvl.shared.audit.AggregateType;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.WorkflowAction;
import java.time.Instant;
import com.tttn.qlnvl.shared.audit.StatusHistoryService;
import com.tttn.qlnvl.shared.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatusHistoryController.class)
@Import({SecurityConfig.class, BusinessDisplayFormatter.class})
class StatusHistoryControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private StatusHistoryService historyService;

    @Test
    void rendersRecordedEmptyHistory() throws Exception {
        AppUserPrincipal principal = principal(Role.REQUESTER);
        when(historyService.get(AggregateType.REQUEST, 7L, principal))
                .thenReturn(new StatusHistoryService.Timeline("REQ-7", "/requests/7", List.of()));

        mockMvc.perform(get("/workflow-history/REQUEST/7").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("REQ-7")));
    }

    @Test
    void rendersRecordedActorActionTimeAndComment() throws Exception {
        AppUserPrincipal principal = principal(Role.REQUESTER);
        StatusHistory event = mock(StatusHistory.class);
        AppUser actor = mock(AppUser.class);
        when(actor.getFullName()).thenReturn("Actor Name");
        when(event.getChangedBy()).thenReturn(actor);
        when(event.getChangedAt()).thenReturn(Instant.parse("2026-09-22T03:00:00Z"));
        when(event.getAction()).thenReturn(WorkflowAction.SUBMIT);
        when(event.getToStatus()).thenReturn("SUBMITTED");
        when(event.getComment()).thenReturn("Recorded comment");
        when(historyService.get(AggregateType.REQUEST, 7L, principal))
                .thenReturn(new StatusHistoryService.Timeline("REQ-7", "/requests/7", List.of(event)));

        mockMvc.perform(get("/workflow-history/REQUEST/7").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Actor Name")))
                .andExpect(content().string(containsString("SUBMIT")))
                .andExpect(content().string(containsString("22/09/2026 10:00")))
                .andExpect(content().string(containsString("Recorded comment")));
    }

    @Test
    void unauthenticatedViewerIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/workflow-history/REQUEST/7"))
                .andExpect(status().is3xxRedirection());
    }

    private AppUserPrincipal principal(Role role) {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(11L);
        when(user.getRole()).thenReturn(role);
        when(user.getUsername()).thenReturn("viewer");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getFullName()).thenReturn("Viewer");
        return AppUserPrincipal.from(user);
    }
}
