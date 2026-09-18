package com.tttn.qlnvl.inventory.web;

import com.tttn.qlnvl.inventory.application.InventoryReportService;
import com.tttn.qlnvl.inventory.application.InventoryReportService.DetailedInventoryReport;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class InventoryReportController {
    private static final List<Integer> PAGE_SIZES = List.of(20, 50, 100);

    private final InventoryReportService reportService;

    public InventoryReportController(InventoryReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/reports/detailed-inventory")
    String detailedInventory(@RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long materialGroupId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false, name = "condition")
                    List<MaterialCondition> conditions,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        DetailedInventoryReport report = reportService.detailedInventory(warehouseId,
                materialGroupId, materialId, conditions, page, size);
        model.addAttribute("lotPage", report.lots());
        model.addAttribute("aggregates", report.aggregates());
        model.addAttribute("warehouses", reportService.activeWarehouses());
        model.addAttribute("materialGroups", reportService.activeMaterialGroups());
        model.addAttribute("materials", reportService.activeMaterials());
        model.addAttribute("conditions", MaterialCondition.values());
        model.addAttribute("pageSizes", PAGE_SIZES);
        model.addAttribute("warehouseId", warehouseId);
        model.addAttribute("materialGroupId", materialGroupId);
        model.addAttribute("materialId", materialId);
        model.addAttribute("selectedConditions", conditions == null ? List.of() : conditions);
        model.addAttribute("selectedSize", PAGE_SIZES.contains(size) ? size : 20);
        return "reports/detailed-inventory";
    }
}