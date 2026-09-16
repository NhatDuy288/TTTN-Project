package com.tttn.qlnvl.warehouserequest.web;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.warehouserequest.application.InvalidWarehouseRequestException;
import com.tttn.qlnvl.warehouserequest.application.RequestApprovalService;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestConflictException;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('REQUEST_APPROVER')")
public class RequestApprovalController {
    private final RequestApprovalService approvalService;

    public RequestApprovalController(RequestApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/request-approvals")
    String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Model model) {
        int selectedSize = List.of(20, 50, 100).contains(size) ? size : 20;
        model.addAttribute("pageData", approvalService.queue(page, size));
        model.addAttribute("pageSizes", List.of(20, 50, 100));
        model.addAttribute("selectedSize", selectedSize);
        return "request-approvals/list";
    }

    @GetMapping("/request-approvals/{id}")
    String detail(@PathVariable Long id, Model model) {
        model.addAttribute("request", approvalService.getDetail(id));
        return "request-approvals/detail";
    }

    @PostMapping("/request-approvals/{id}/approve")
    String approve(@PathVariable Long id, @RequestParam(required = false) String comment,
            Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            approvalService.approve(id, actorId(authentication), comment);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã duyệt và tạo chứng từ thực hiện.");
        } catch (WarehouseRequestConflictException | InvalidWarehouseRequestException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/request-approvals/" + id;
    }

    @PostMapping("/request-approvals/{id}/reject")
    String reject(@PathVariable Long id, @RequestParam(required = false) String comment,
            Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            approvalService.reject(id, actorId(authentication), comment);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối phiếu đề nghị.");
        } catch (WarehouseRequestConflictException | InvalidWarehouseRequestException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/request-approvals/" + id;
    }

    private Long actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        throw new WarehouseRequestAccessDeniedException();
    }
}
