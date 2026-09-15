package com.tttn.qlnvl.warehouse.web;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.shared.application.DuplicateMasterDataException;
import com.tttn.qlnvl.shared.application.InvalidMasterDataException;
import com.tttn.qlnvl.warehouse.application.WarehouseService;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.domain.WarehouseStatus;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('WAREHOUSE_KEEPER')")
public class WarehouseCommandController {
    private final WarehouseService warehouseService;

    public WarehouseCommandController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping("/warehouses/new")
    String createForm(Model model) {
        model.addAttribute("warehouseForm", new WarehouseCreateForm());
        model.addAttribute("editMode", false);
        return "warehouses/form";
    }

    @PostMapping("/warehouses")
    String create(@Valid @ModelAttribute("warehouseForm") WarehouseCreateForm form,
                  BindingResult bindingResult,
                  @AuthenticationPrincipal AppUserPrincipal principal,
                  Model model,
                  RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editMode", false);
            return "warehouses/form";
        }
        try {
            Warehouse warehouse = warehouseService.create(form.getWarehouseCode(), form.getWarehouseName(),
                    form.getAddress(), form.getNote(), principal.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo kho thành công.");
            return "redirect:/warehouses/" + warehouse.getId();
        } catch (DuplicateMasterDataException | InvalidMasterDataException exception) {
            reject(bindingResult, exception);
            model.addAttribute("editMode", false);
            return "warehouses/form";
        }
    }

    @GetMapping("/warehouses/{id}/edit")
    String editForm(@PathVariable Long id, Model model) {
        Warehouse warehouse = warehouseService.get(id);
        model.addAttribute("warehouse", warehouse);
        model.addAttribute("warehouseForm", WarehouseUpdateForm.from(warehouse));
        model.addAttribute("statuses", WarehouseStatus.values());
        model.addAttribute("editMode", true);
        return "warehouses/form";
    }

    @PostMapping("/warehouses/{id}")
    String update(@PathVariable Long id,
                  @Valid @ModelAttribute("warehouseForm") WarehouseUpdateForm form,
                  BindingResult bindingResult,
                  @AuthenticationPrincipal AppUserPrincipal principal,
                  Model model,
                  RedirectAttributes redirectAttributes) {
        Warehouse warehouse = warehouseService.get(id);
        if (bindingResult.hasErrors()) {
            prepareEditModel(model, warehouse);
            return "warehouses/form";
        }
        try {
            warehouseService.update(id, form.getWarehouseName(), form.getAddress(), form.getNote(),
                    form.getStatus(), principal.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật kho thành công.");
            return "redirect:/warehouses/" + id;
        } catch (DuplicateMasterDataException | InvalidMasterDataException exception) {
            reject(bindingResult, exception);
            prepareEditModel(model, warehouse);
            return "warehouses/form";
        }
    }

    private void prepareEditModel(Model model, Warehouse warehouse) {
        model.addAttribute("warehouse", warehouse);
        model.addAttribute("statuses", WarehouseStatus.values());
        model.addAttribute("editMode", true);
    }

    private void reject(BindingResult bindingResult, RuntimeException exception) {
        String field = exception instanceof DuplicateMasterDataException duplicate
                ? duplicate.getField()
                : ((InvalidMasterDataException) exception).getField();
        if (field == null) {
            bindingResult.reject("duplicate", exception.getMessage());
        } else {
            bindingResult.rejectValue(field, "invalid", exception.getMessage());
        }
    }
}
