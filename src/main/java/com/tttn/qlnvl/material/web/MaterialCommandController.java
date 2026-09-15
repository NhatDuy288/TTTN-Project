package com.tttn.qlnvl.material.web;

import com.tttn.qlnvl.material.application.MaterialService;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialStatus;
import com.tttn.qlnvl.material.domain.MaterialUnit;
import com.tttn.qlnvl.shared.application.DuplicateMasterDataException;
import com.tttn.qlnvl.shared.application.InvalidMasterDataException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('INVENTORY_STAFF')")
public class MaterialCommandController {
    private final MaterialService materialService;

    public MaterialCommandController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping("/materials/new")
    String createForm(Model model) {
        model.addAttribute("materialForm", new MaterialCreateForm());
        addReferenceData(model);
        model.addAttribute("editMode", false);
        return "materials/form";
    }

    @PostMapping("/materials")
    String create(@Valid @ModelAttribute("materialForm") MaterialCreateForm form,
                  BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addReferenceData(model);
            model.addAttribute("editMode", false);
            return "materials/form";
        }
        try {
            Material material = materialService.create(form.getMaterialGroupId(), form.getMaterialCode(),
                    form.getMaterialName(), form.getUnit(), form.getGlCode());
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo vật tư thành công.");
            return "redirect:/materials/" + material.getId();
        } catch (DuplicateMasterDataException | InvalidMasterDataException exception) {
            reject(bindingResult, exception);
            addReferenceData(model);
            model.addAttribute("editMode", false);
            return "materials/form";
        }
    }

    @GetMapping("/materials/{id}/edit")
    String editForm(@PathVariable Long id, Model model) {
        Material material = materialService.get(id);
        model.addAttribute("material", material);
        model.addAttribute("materialForm", MaterialUpdateForm.from(material));
        addReferenceData(model);
        model.addAttribute("editMode", true);
        return "materials/form";
    }

    @PostMapping("/materials/{id}")
    String update(@PathVariable Long id,
                  @Valid @ModelAttribute("materialForm") MaterialUpdateForm form,
                  BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        Material material = materialService.get(id);
        if (bindingResult.hasErrors()) {
            prepareEditModel(model, material);
            return "materials/form";
        }
        try {
            materialService.update(id, form.getMaterialName(), form.getUnit(), form.getGlCode(), form.getStatus());
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật vật tư thành công.");
            return "redirect:/materials/" + id;
        } catch (DuplicateMasterDataException | InvalidMasterDataException exception) {
            reject(bindingResult, exception);
            prepareEditModel(model, material);
            return "materials/form";
        }
    }

    private void addReferenceData(Model model) {
        model.addAttribute("groups", materialService.activeGroups());
        model.addAttribute("units", MaterialUnit.values());
        model.addAttribute("statuses", MaterialStatus.values());
    }

    private void prepareEditModel(Model model, Material material) {
        model.addAttribute("material", material);
        addReferenceData(model);
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
