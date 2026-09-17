package com.tttn.qlnvl.warehousetransfer.web;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.warehousetransfer.application.WarehouseTransferConflictException;
import com.tttn.qlnvl.warehousetransfer.application.WarehouseTransferService;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
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
@PreAuthorize("hasRole('INVENTORY_STAFF')")
public class WarehouseTransferController {
    private final WarehouseTransferService transferService;

    public WarehouseTransferController(WarehouseTransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping("/warehouse-transfers")
    String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "DRAFT") WarehouseTransferStatus status,
            Model model) {
        int selectedSize = List.of(20, 50, 100).contains(size) ? size : 20;
        model.addAttribute("pageData", transferService.queue(page, size, status));
        model.addAttribute("statuses", WarehouseTransferStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("pageSizes", List.of(20, 50, 100));
        model.addAttribute("selectedSize", selectedSize);
        return "warehouse-transfers/list";
    }

    @GetMapping("/warehouse-transfers/{id}")
    String detail(@PathVariable Long id, Model model) {
        model.addAttribute("transfer", transferService.getDetail(id));
        return "warehouse-transfers/detail";
    }

    @PostMapping("/warehouse-transfers/{id}/submit")
    String submit(@PathVariable Long id, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            transferService.submit(id, actorId(authentication));
        } catch (WarehouseTransferConflictException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/warehouse-transfers/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã chuyển duyệt chứng từ điều chuyển kho.");
        return "redirect:/warehouse-transfers/" + id;
    }

    private Long actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        throw new AccessDeniedException("Không có quyền thực hiện thao tác này.");
    }
}
