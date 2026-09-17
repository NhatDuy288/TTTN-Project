package com.tttn.qlnvl.warehousetransaction.web;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.warehousetransaction.application.InvalidWarehouseTransactionException;
import com.tttn.qlnvl.warehousetransaction.application.TransactionApprovalService;
import com.tttn.qlnvl.warehousetransaction.application.WarehouseTransactionConflictException;
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
public class TransactionApprovalController {
    private final TransactionApprovalService approvalService;

    public TransactionApprovalController(TransactionApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/transaction-approvals")
    String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Model model) {
        int selectedSize = List.of(20, 50, 100).contains(size) ? size : 20;
        model.addAttribute("pageData", approvalService.queue(page, size));
        model.addAttribute("pageSizes", List.of(20, 50, 100));
        model.addAttribute("selectedSize", selectedSize);
        return "transaction-approvals/list";
    }

    @GetMapping("/transaction-approvals/{id}")
    String detail(@PathVariable Long id, Model model) {
        model.addAttribute("transaction", approvalService.getDetail(id));
        model.addAttribute("decisionForm", new TransactionDecisionForm());
        return "transaction-approvals/detail";
    }

    @PostMapping("/transaction-approvals/{id}/approve")
    String approve(@PathVariable Long id,
            @Valid @ModelAttribute("decisionForm") TransactionDecisionForm form,
            BindingResult bindingResult, Authentication authentication, Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return renderDetail(id, model);
        try {
            approvalService.approve(id, actorId(authentication), form.getComment());
        } catch (InvalidWarehouseTransactionException exception) {
            bindingResult.rejectValue(exception.getField(), "invalid", exception.getMessage());
            return renderDetail(id, model);
        } catch (WarehouseTransactionConflictException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/transaction-approvals/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã duyệt chứng từ và chuyển sang chờ xác nhận kho.");
        return "redirect:/transaction-approvals/" + id;
    }

    @PostMapping("/transaction-approvals/{id}/reject")
    String reject(@PathVariable Long id,
            @Valid @ModelAttribute("decisionForm") TransactionDecisionForm form,
            BindingResult bindingResult, Authentication authentication, Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return renderDetail(id, model);
        try {
            approvalService.reject(id, actorId(authentication), form.getComment());
        } catch (InvalidWarehouseTransactionException exception) {
            bindingResult.rejectValue(exception.getField(), "invalid", exception.getMessage());
            return renderDetail(id, model);
        } catch (WarehouseTransactionConflictException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/transaction-approvals/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã từ chối chứng từ và hủy phiếu đề nghị liên quan.");
        return "redirect:/transaction-approvals/" + id;
    }

    private String renderDetail(Long id, Model model) {
        model.addAttribute("transaction", approvalService.getDetail(id));
        return "transaction-approvals/detail";
    }

    private Long actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        throw new AccessDeniedException("Không có quyền thực hiện thao tác này.");
    }
}
