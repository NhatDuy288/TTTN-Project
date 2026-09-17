package com.tttn.qlnvl.warehousetransaction.web;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.warehousetransaction.application.TransactionConfirmationService;
import com.tttn.qlnvl.warehousetransaction.application.WarehouseTransactionConflictException;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
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
@PreAuthorize("hasRole('WAREHOUSE_KEEPER')")
public class TransactionConfirmationController {
    private final TransactionConfirmationService confirmationService;

    public TransactionConfirmationController(TransactionConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    @GetMapping("/transaction-confirmations")
    String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Model model) {
        int selectedSize = List.of(20, 50, 100).contains(size) ? size : 20;
        model.addAttribute("pageData", confirmationService.queue(page, size));
        model.addAttribute("pageSizes", List.of(20, 50, 100));
        model.addAttribute("selectedSize", selectedSize);
        return "transaction-confirmations/list";
    }

    @GetMapping("/transaction-confirmations/{id}")
    String detail(@PathVariable Long id, Model model) {
        model.addAttribute("transaction", confirmationService.getDetail(id));
        return "transaction-confirmations/detail";
    }

    @PostMapping("/transaction-confirmations/{id}/confirm")
    String confirm(@PathVariable Long id, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            confirmationService.confirm(id, actorId(authentication));
        } catch (WarehouseTransactionConflictException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/transaction-confirmations/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã xác nhận movement vật lý và hoàn tất chứng từ.");
        return "redirect:/transaction-confirmations/" + id;
    }

    private Long actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        throw new AccessDeniedException("Không có quyền thực hiện thao tác này.");
    }
}
