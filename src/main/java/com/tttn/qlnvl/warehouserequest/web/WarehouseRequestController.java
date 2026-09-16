package com.tttn.qlnvl.warehouserequest.web;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.warehouserequest.application.InvalidWarehouseRequestException;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestFormOptions;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestOptionService;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestService;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import jakarta.validation.Valid;
import java.util.List;
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
@PreAuthorize("hasRole('REQUESTER')")
public class WarehouseRequestController {
    private final WarehouseRequestService requestService;
    private final WarehouseRequestOptionService optionService;

    public WarehouseRequestController(WarehouseRequestService requestService,
            WarehouseRequestOptionService optionService) {
        this.requestService = requestService;
        this.optionService = optionService;
    }

    @GetMapping("/requests")
    String list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) WarehouseRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication, Model model) {
        int selectedSize = List.of(20, 50, 100).contains(size) ? size : 20;
        model.addAttribute("pageData", requestService.searchOwned(
                actorId(authentication), keyword, status, page, size));
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", WarehouseRequestStatus.values());
        model.addAttribute("pageSizes", List.of(20, 50, 100));
        model.addAttribute("selectedSize", selectedSize);
        return "requests/list";
    }

    @GetMapping("/requests/new")
    String createForm(@RequestParam(required = false) Long operationTypeId, Model model) {
        WarehouseRequestForm form = new WarehouseRequestForm();
        form.setOperationTypeId(operationTypeId);
        model.addAttribute("warehouseRequestForm", form);
        prepareForm(model, false, null);
        return "requests/form";
    }

    @PostMapping("/requests")
    String create(@Valid @ModelAttribute("warehouseRequestForm") WarehouseRequestForm form,
            BindingResult bindingResult, Authentication authentication, Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, false, null);
            return "requests/form";
        }
        try {
            WarehouseRequest request = requestService.createDraft(form.toCommand(), actorId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu nháp phiếu đề nghị.");
            return "redirect:/requests/" + request.getId();
        } catch (InvalidWarehouseRequestException exception) {
            reject(bindingResult, exception);
            prepareForm(model, false, null);
            return "requests/form";
        }
    }

    @GetMapping("/requests/{id}")
    String detail(@PathVariable Long id, Authentication authentication, Model model) {
        model.addAttribute("request", requestService.getOwned(id, actorId(authentication)));
        return "requests/detail";
    }

    @GetMapping("/requests/{id}/edit")
    String editForm(@PathVariable Long id, Authentication authentication, Model model) {
        WarehouseRequest request = requestService.getOwnedDraft(id, actorId(authentication));
        model.addAttribute("warehouseRequestForm", WarehouseRequestForm.from(request));
        prepareForm(model, true, request);
        return "requests/form";
    }

    @PostMapping("/requests/{id}")
    String update(@PathVariable Long id,
            @Valid @ModelAttribute("warehouseRequestForm") WarehouseRequestForm form,
            BindingResult bindingResult, Authentication authentication, Model model,
            RedirectAttributes redirectAttributes) {
        WarehouseRequest current = requestService.getOwnedDraft(id, actorId(authentication));
        if (bindingResult.hasErrors()) {
            prepareForm(model, true, current);
            return "requests/form";
        }
        try {
            requestService.updateDraft(id, form.toCommand(), actorId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật phiếu đề nghị nháp.");
            return "redirect:/requests/" + id;
        } catch (InvalidWarehouseRequestException exception) {
            reject(bindingResult, exception);
            prepareForm(model, true, current);
            return "requests/form";
        }
    }

    @PostMapping("/requests/{id}/submit")
    String submit(@PathVariable Long id, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            requestService.submit(id, actorId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Đã chuyển duyệt phiếu đề nghị.");
        } catch (InvalidWarehouseRequestException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/requests/" + id;
    }

    @PostMapping("/requests/{id}/cancel")
    String cancel(@PathVariable Long id, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        requestService.cancel(id, actorId(authentication));
        redirectAttributes.addFlashAttribute("successMessage", "Đã hủy phiếu đề nghị.");
        return "redirect:/requests/" + id;
    }

    private void prepareForm(Model model, boolean editMode, WarehouseRequest request) {
        WarehouseRequestFormOptions options = optionService.load();
        model.addAttribute("options", options);
        model.addAttribute("conditions", MaterialCondition.values());
        model.addAttribute("editMode", editMode);
        model.addAttribute("request", request);
    }

    private Long actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        throw new WarehouseRequestAccessDeniedException();
    }

    private void reject(BindingResult bindingResult, InvalidWarehouseRequestException exception) {
        if (exception.getField() == null) {
            bindingResult.reject("invalid", exception.getMessage());
        } else {
            bindingResult.rejectValue(exception.getField(), "invalid", exception.getMessage());
        }
    }
}
