package com.tttn.qlnvl.warehousetransaction.web;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.warehousetransaction.application.InvalidWarehouseTransactionException;
import com.tttn.qlnvl.warehousetransaction.application.WarehouseTransactionService;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
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
@PreAuthorize("hasRole('INVENTORY_STAFF')")
public class WarehouseTransactionController {
    private final WarehouseTransactionService transactionService;

    public WarehouseTransactionController(WarehouseTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/warehouse-transactions")
    String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "DRAFT") WarehouseTransactionStatus status,
            Model model) {
        int selectedSize = List.of(20, 50, 100).contains(size) ? size : 20;
        model.addAttribute("pageData", transactionService.queue(page, size, status));
        model.addAttribute("statuses", WarehouseTransactionStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("pageSizes", List.of(20, 50, 100));
        model.addAttribute("selectedSize", selectedSize);
        return "warehouse-transactions/list";
    }

    @GetMapping("/warehouse-transactions/{id}")
    String detail(@PathVariable Long id, Model model) {
        WarehouseTransaction transaction = transactionService.getDetail(id);
        model.addAttribute("transaction", transaction);
        model.addAttribute("submitForm", new TransactionSubmitForm(transaction.getExecutionNote()));
        return "warehouse-transactions/detail";
    }

    @PostMapping("/warehouse-transactions/{id}/submit")
    String submit(@PathVariable Long id,
            @Valid @ModelAttribute("submitForm") TransactionSubmitForm form,
            BindingResult bindingResult, Authentication authentication, Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderDetail(id, model);
        }
        try {
            transactionService.submit(id, actorId(authentication), form.getExecutionNote());
        } catch (InvalidWarehouseTransactionException exception) {
            bindingResult.rejectValue(exception.getField(), "invalid", exception.getMessage());
            return renderDetail(id, model);
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã chuyển duyệt chứng từ nhập/xuất kho.");
        return "redirect:/warehouse-transactions/" + id;
    }

    private String renderDetail(Long id, Model model) {
        model.addAttribute("transaction", transactionService.getDetail(id));
        return "warehouse-transactions/detail";
    }

    private Long actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        throw new AccessDeniedException("Không có quyền thực hiện thao tác này.");
    }
}
