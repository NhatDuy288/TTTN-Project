package com.tttn.qlnvl.warehousetransfer.web;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.warehousetransfer.application.TransferConfirmationService;
import com.tttn.qlnvl.warehousetransfer.application.WarehouseTransferConflictException;
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
public class TransferConfirmationController {
    private final TransferConfirmationService confirmationService;

    public TransferConfirmationController(TransferConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    @GetMapping("/transfer-confirmations/source")
    String sourceList(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Model model) {
        int selectedSize = List.of(20, 50, 100).contains(size) ? size : 20;
        model.addAttribute("pageData", confirmationService.sourceQueue(page, size));
        model.addAttribute("pageSizes", List.of(20, 50, 100));
        model.addAttribute("selectedSize", selectedSize);
        return "transfer-confirmations/source-list";
    }

    @GetMapping("/transfer-confirmations/source/{id}")
    String sourceDetail(@PathVariable Long id, Model model) {
        model.addAttribute("transfer", confirmationService.getDetail(id));
        return "transfer-confirmations/source-detail";
    }

    @GetMapping("/transfer-confirmations/destination")
    String destinationList(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Model model) {
        int selectedSize = List.of(20, 50, 100).contains(size) ? size : 20;
        model.addAttribute("pageData", confirmationService.destinationQueue(page, size));
        model.addAttribute("pageSizes", List.of(20, 50, 100));
        model.addAttribute("selectedSize", selectedSize);
        return "transfer-confirmations/destination-list";
    }

    @GetMapping("/transfer-confirmations/destination/{id}")
    String destinationDetail(@PathVariable Long id, Model model) {
        model.addAttribute("transfer", confirmationService.getDetail(id));
        return "transfer-confirmations/destination-detail";
    }

    @PostMapping("/transfer-confirmations/source/{id}/confirm")
    String confirmSource(@PathVariable Long id, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            confirmationService.confirmSource(id, actorId(authentication));
        } catch (WarehouseTransferConflictException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/transfer-confirmations/source/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã xác nhận xuất kho nguồn và chuyển điều chuyển sang đang vận chuyển.");
        return "redirect:/transfer-confirmations/source/" + id;
    }

    @PostMapping("/transfer-confirmations/destination/{id}/confirm")
    String confirmDestination(@PathVariable Long id, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            confirmationService.confirmDestination(id, actorId(authentication));
        } catch (WarehouseTransferConflictException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/transfer-confirmations/destination/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã xác nhận nhập kho đích và hoàn tất điều chuyển.");
        return "redirect:/transfer-confirmations/destination/" + id;
    }

    private Long actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        throw new AccessDeniedException("Không có quyền thực hiện thao tác này.");
    }
}
