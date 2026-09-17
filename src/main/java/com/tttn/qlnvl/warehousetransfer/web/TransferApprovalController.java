package com.tttn.qlnvl.warehousetransfer.web;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.warehousetransfer.application.InvalidWarehouseTransferException;
import com.tttn.qlnvl.warehousetransfer.application.TransferApprovalService;
import com.tttn.qlnvl.warehousetransfer.application.WarehouseTransferConflictException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('INVENTORY_APPROVER')")
public class TransferApprovalController {
    private final TransferApprovalService approvalService;

    public TransferApprovalController(TransferApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/transfer-approvals")
    String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Model model) {
        int selectedSize = List.of(20, 50, 100).contains(size) ? size : 20;
        model.addAttribute("pageData", approvalService.queue(page, size));
        model.addAttribute("pageSizes", List.of(20, 50, 100));
        model.addAttribute("selectedSize", selectedSize);
        return "transfer-approvals/list";
    }

    @GetMapping("/transfer-approvals/{id}")
    String detail(@PathVariable Long id, Model model) {
        model.addAttribute("transfer", approvalService.getDetail(id));
        model.addAttribute("decisionForm", new TransferDecisionForm());
        return "transfer-approvals/detail";
    }

    @PostMapping("/transfer-approvals/{id}/approve")
    String approve(@PathVariable Long id,
            @Valid @ModelAttribute("decisionForm") TransferDecisionForm form,
            BindingResult bindingResult, Authentication authentication, Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return renderDetail(id, model);
        try {
            approvalService.approve(id, actorId(authentication), form.getComment());
        } catch (InvalidWarehouseTransferException exception) {
            bindingResult.rejectValue(exception.getField(), "invalid", exception.getMessage());
            return renderDetail(id, model);
        } catch (WarehouseTransferConflictException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/transfer-approvals/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã duyệt điều chuyển và chuyển sang sẵn sàng xuất kho nguồn.");
        return "redirect:/transfer-approvals/" + id;
    }

    @PostMapping("/transfer-approvals/{id}/reject")
    String reject(@PathVariable Long id,
            @Valid @ModelAttribute("decisionForm") TransferDecisionForm form,
            BindingResult bindingResult, Authentication authentication, Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return renderDetail(id, model);
        try {
            approvalService.reject(id, actorId(authentication), form.getComment());
        } catch (InvalidWarehouseTransferException exception) {
            bindingResult.rejectValue(exception.getField(), "invalid", exception.getMessage());
            return renderDetail(id, model);
        } catch (WarehouseTransferConflictException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/transfer-approvals/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã từ chối điều chuyển, hủy phiếu đề nghị và giải phóng giữ chỗ theo lô.");
        return "redirect:/transfer-approvals/" + id;
    }

    private String renderDetail(Long id, Model model) {
        model.addAttribute("transfer", approvalService.getDetail(id));
        return "transfer-approvals/detail";
    }

    private Long actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        throw new AccessDeniedException("Không có quyền thực hiện thao tác này.");
    }
}
