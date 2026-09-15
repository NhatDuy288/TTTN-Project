package com.tttn.qlnvl.purchaseorder.web;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.purchaseorder.application.InvalidPurchaseOrderException;
import com.tttn.qlnvl.purchaseorder.application.PurchaseOrderDetail;
import com.tttn.qlnvl.purchaseorder.application.PurchaseOrderSearch;
import com.tttn.qlnvl.purchaseorder.application.PurchaseOrderService;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PurchaseOrderQueryController {
    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderQueryController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping("/purchase-orders")
    String list(
            @RequestParam(required = false) LocalDate createdFrom,
            @RequestParam(required = false) LocalDate createdTo,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) String poCode,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) Long materialGroupId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String supplierName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication,
            Model model) {
        PurchaseOrderSearch defaults = purchaseOrderService.defaultSearch();
        PurchaseOrderSearch search = new PurchaseOrderSearch(
                createdFrom == null ? defaults.createdFrom() : createdFrom,
                createdTo == null ? defaults.createdTo() : createdTo,
                status, poCode, createdBy, materialGroupId, materialId, supplierName);
        try {
            model.addAttribute("pageData", purchaseOrderService.search(search, page, size));
        } catch (InvalidPurchaseOrderException exception) {
            model.addAttribute("pageData", Page.empty());
            model.addAttribute("searchError", exception.getMessage());
        }
        addSearchModel(model, search, size);
        model.addAttribute("canManage", hasManageRole(authentication));
        return "purchase-orders/list";
    }

    @GetMapping("/purchase-orders/{id}")
    String detail(@PathVariable Long id, Authentication authentication, Model model) {
        PurchaseOrderDetail detail = purchaseOrderService.getDetail(id);
        model.addAttribute("detail", detail);
        model.addAttribute("canManage", canManage(authentication, detail));
        return "purchase-orders/detail";
    }

    private void addSearchModel(Model model, PurchaseOrderSearch search, int size) {
        model.addAttribute("search", search);
        model.addAttribute("statuses", PurchaseOrderStatus.values());
        model.addAttribute("groups", purchaseOrderService.allGroups());
        model.addAttribute("materials", purchaseOrderService.allMaterials());
        model.addAttribute("pageSizes", List.of(20, 50, 100));
        model.addAttribute("selectedSize", List.of(20, 50, 100).contains(size) ? size : 20);
    }

    private boolean canManage(Authentication authentication, PurchaseOrderDetail detail) {
        if (!hasManageRole(authentication) || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return false;
        }
        return principal.getId().equals(detail.order().getCreatedBy().getId());
    }

    private boolean hasManageRole(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_REQUESTER")
                        || authority.getAuthority().equals("ROLE_INVENTORY_STAFF"));
    }
}
