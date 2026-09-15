package com.tttn.qlnvl.warehouse.web;

import com.tttn.qlnvl.warehouse.application.WarehouseService;
import com.tttn.qlnvl.warehouse.domain.WarehouseStatus;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WarehouseQueryController {
    private final WarehouseService warehouseService;

    public WarehouseQueryController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping("/warehouses")
    String list(@RequestParam(required = false) String keyword,
                @RequestParam(required = false) WarehouseStatus status,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "20") int size,
                Authentication authentication,
                Model model) {
        model.addAttribute("pageData", warehouseService.search(keyword, status, page, size));
        model.addAttribute("statuses", WarehouseStatus.values());
        model.addAttribute("pageSizes", List.of(20, 50, 100));
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedSize", List.of(20, 50, 100).contains(size) ? size : 20);
        model.addAttribute("canManage", hasRole(authentication, "ROLE_WAREHOUSE_KEEPER"));
        return "warehouses/list";
    }

    @GetMapping("/warehouses/{id}")
    String detail(@PathVariable Long id, Authentication authentication, Model model) {
        model.addAttribute("warehouse", warehouseService.get(id));
        model.addAttribute("canManage", hasRole(authentication, "ROLE_WAREHOUSE_KEEPER"));
        return "warehouses/detail";
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }
}
