package com.tttn.qlnvl.material.web;

import com.tttn.qlnvl.material.application.MaterialService;
import com.tttn.qlnvl.material.domain.MaterialStatus;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MaterialQueryController {
    private final MaterialService materialService;

    public MaterialQueryController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping("/materials")
    String list(@RequestParam(required = false) Long materialGroupId,
                @RequestParam(required = false) String codeOrName,
                @RequestParam(required = false) MaterialStatus status,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "20") int size,
                Authentication authentication,
                Model model) {
        model.addAttribute("pageData",
                materialService.search(materialGroupId, codeOrName, status, page, size));
        model.addAttribute("groups", materialService.allGroups());
        model.addAttribute("statuses", MaterialStatus.values());
        model.addAttribute("pageSizes", List.of(20, 50, 100));
        model.addAttribute("materialGroupId", materialGroupId);
        model.addAttribute("codeOrName", codeOrName);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedSize", List.of(20, 50, 100).contains(size) ? size : 20);
        model.addAttribute("canManage", hasRole(authentication, "ROLE_INVENTORY_STAFF"));
        return "materials/list";
    }

    @GetMapping("/materials/{id}")
    String detail(@PathVariable Long id, Authentication authentication, Model model) {
        model.addAttribute("material", materialService.get(id));
        model.addAttribute("canManage", hasRole(authentication, "ROLE_INVENTORY_STAFF"));
        return "materials/detail";
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }
}
