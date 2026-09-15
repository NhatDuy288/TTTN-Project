package com.tttn.qlnvl.purchaseorder.web;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.purchaseorder.application.InvalidPurchaseOrderException;
import com.tttn.qlnvl.purchaseorder.application.PurchaseOrderService;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasAnyRole('REQUESTER', 'INVENTORY_STAFF')")
public class PurchaseOrderCommandController {
    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderCommandController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping("/purchase-orders/new")
    String createForm(Model model) {
        model.addAttribute("purchaseOrderForm", new PurchaseOrderForm());
        prepareForm(model, false, null);
        return "purchase-orders/form";
    }

    @PostMapping("/purchase-orders")
    String create(
            @Valid @ModelAttribute("purchaseOrderForm") PurchaseOrderForm form,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, false, null);
            return "purchase-orders/form";
        }
        try {
            PurchaseOrder order = purchaseOrderService.createDraft(form.toCommand(), actorId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu nháp đơn hàng.");
            return "redirect:/purchase-orders/" + order.getId();
        } catch (InvalidPurchaseOrderException exception) {
            reject(bindingResult, exception);
            prepareForm(model, false, null);
            return "purchase-orders/form";
        }
    }

    @GetMapping("/purchase-orders/{id}/edit")
    String editForm(@PathVariable Long id, Authentication authentication, Model model) {
        PurchaseOrder order = purchaseOrderService.getOwnedDraft(id, actorId(authentication));
        model.addAttribute("purchaseOrderForm", PurchaseOrderForm.from(order));
        prepareForm(model, true, order);
        return "purchase-orders/form";
    }

    @PostMapping("/purchase-orders/{id}")
    String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("purchaseOrderForm") PurchaseOrderForm form,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        PurchaseOrder current = purchaseOrderService.getOwnedDraft(id, actorId(authentication));
        if (bindingResult.hasErrors()) {
            prepareForm(model, true, current);
            return "purchase-orders/form";
        }
        try {
            purchaseOrderService.updateDraft(id, form.toCommand(), actorId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật đơn hàng nháp.");
            return "redirect:/purchase-orders/" + id;
        } catch (InvalidPurchaseOrderException exception) {
            reject(bindingResult, exception);
            prepareForm(model, true, current);
            return "purchase-orders/form";
        }
    }

    @PostMapping("/purchase-orders/{id}/finalize")
    String finalizeOrder(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            purchaseOrderService.finalizeOrder(id, actorId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Đã hoàn tất khai báo và mở đơn hàng.");
            return "redirect:/purchase-orders/" + id;
        } catch (InvalidPurchaseOrderException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/purchase-orders/" + id + "/edit";
        }
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    String cancel(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        purchaseOrderService.cancel(id, actorId(authentication));
        redirectAttributes.addFlashAttribute("successMessage", "Đã hủy đơn hàng.");
        return "redirect:/purchase-orders/" + id;
    }

    private void prepareForm(Model model, boolean editMode, PurchaseOrder order) {
        model.addAttribute("materials", purchaseOrderService.activeMaterials());
        model.addAttribute("groups", purchaseOrderService.activeGroups());
        model.addAttribute("editMode", editMode);
        model.addAttribute("order", order);
    }

    private Long actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        throw new PurchaseOrderAccessDeniedException();
    }

    private void reject(BindingResult bindingResult, InvalidPurchaseOrderException exception) {
        if (exception.getField() == null || "items".equals(exception.getField())) {
            bindingResult.reject("invalid", exception.getMessage());
        } else {
            bindingResult.rejectValue(exception.getField(), "invalid", exception.getMessage());
        }
    }
}
