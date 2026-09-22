package com.tttn.qlnvl.shared.web;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.shared.audit.AggregateType;
import com.tttn.qlnvl.shared.audit.StatusHistoryService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class StatusHistoryController {
    private final StatusHistoryService historyService;

    public StatusHistoryController(StatusHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/workflow-history/{aggregateType}/{aggregateId}")
    String detail(@PathVariable AggregateType aggregateType, @PathVariable Long aggregateId,
            Authentication authentication, Model model) {
        model.addAttribute("timeline", historyService.get(aggregateType, aggregateId,
                (AppUserPrincipal) authentication.getPrincipal()));
        return "workflow-history/detail";
    }
}
