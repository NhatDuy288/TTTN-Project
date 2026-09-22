package com.tttn.qlnvl.inventory.web;

import com.tttn.qlnvl.inventory.application.InventoryReportService;
import com.tttn.qlnvl.inventory.application.InventoryReportService.DetailedInventoryReport;
import com.tttn.qlnvl.inventory.application.NxtReportService;
import com.tttn.qlnvl.inventory.application.NxtReportService.InvalidSearchException;
import com.tttn.qlnvl.inventory.application.NxtReportService.MissingOpeningException;
import com.tttn.qlnvl.inventory.application.NxtReportService.NxtReport;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class InventoryReportController {
    private static final List<Integer> PAGE_SIZES = List.of(20, 50, 100);

    private final InventoryReportService reportService;
    private final NxtReportService nxtReportService;

    public InventoryReportController(InventoryReportService reportService,
            NxtReportService nxtReportService) {
        this.reportService = reportService;
        this.nxtReportService = nxtReportService;
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

    @GetMapping("/reports/nxt")
    String nxt(@RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long materialGroupId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false, name = "condition")
                    List<MaterialCondition> conditions,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model, HttpServletResponse response) {
        boolean searched = warehouseId != null || materialGroupId != null || materialId != null
                || (conditions != null && !conditions.isEmpty())
                || fromDate != null || toDate != null;
        LocalDate selectedFromDate = fromDate == null
                ? nxtReportService.defaultFromDate() : fromDate;
        LocalDate selectedToDate = toDate == null
                ? nxtReportService.defaultToDate() : toDate;

        model.addAttribute("warehouses", nxtReportService.warehouses());
        model.addAttribute("materialGroups", nxtReportService.materialGroups());
        model.addAttribute("materials", nxtReportService.materials());
        model.addAttribute("conditions", MaterialCondition.values());
        model.addAttribute("pageSizes", PAGE_SIZES);
        model.addAttribute("warehouseId", warehouseId);
        model.addAttribute("materialGroupId", materialGroupId);
        model.addAttribute("materialId", materialId);
        model.addAttribute("selectedConditions", conditions == null ? List.of() : conditions);
        model.addAttribute("fromDate", selectedFromDate);
        model.addAttribute("toDate", selectedToDate);
        model.addAttribute("selectedSize", PAGE_SIZES.contains(size) ? size : 20);
        model.addAttribute("searched", searched);

        if (!searched) {
            return "reports/nxt";
        }
        try {
            NxtReport report = nxtReportService.report(warehouseId, materialGroupId,
                    materialId, conditions, selectedFromDate, selectedToDate, page, size);
            model.addAttribute("nxtPage", report.rows());
            model.addAttribute("openingDate", report.openingDate());
        } catch (InvalidSearchException exception) {
            model.addAttribute("queryError", exception.getMessage());
        } catch (MissingOpeningException exception) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            model.addAttribute("openingError", exception.getMessage());
            model.addAttribute("openingDate", exception.getOpeningDate());
        }
        return "reports/nxt";
    }
}