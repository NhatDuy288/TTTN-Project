package com.tttn.qlnvl.shared.web;

import java.util.ArrayList;
import java.util.List;
import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/")
    String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    String dashboard(Authentication authentication, Model model) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .orElse("");

        String displayName = authentication.getPrincipal() instanceof AppUserPrincipal principal
                ? principal.getFullName()
                : authentication.getName();

        model.addAttribute("displayName", displayName);
        model.addAttribute("username", authentication.getName());
        model.addAttribute("role", role);
        model.addAttribute("shortcuts", shortcutsFor(role));
        return "dashboard";
    }

    private List<DashboardShortcut> shortcutsFor(String role) {
        List<DashboardShortcut> shortcuts = new ArrayList<>();
        shortcuts.add(new DashboardShortcut("Tra cứu vật tư", "/materials"));
        shortcuts.add(new DashboardShortcut("Tra cứu kho", "/warehouses"));
        shortcuts.add(new DashboardShortcut("Lịch sử nghiệp vụ", "/workflow-history"));

        switch (role) {
            case "REQUESTER" -> {
                shortcuts.add(new DashboardShortcut("Tạo đề nghị kho", "/requests/new"));
                shortcuts.add(new DashboardShortcut("Đơn mua hàng", "/purchase-orders"));
            }
            case "REQUEST_APPROVER" -> {
                shortcuts.add(new DashboardShortcut("Duyệt đề nghị", "/request-approvals"));
                shortcuts.add(new DashboardShortcut("Đơn mua hàng", "/purchase-orders"));
            }
            case "INVENTORY_STAFF" -> {
                shortcuts.add(new DashboardShortcut("Quản lý vật tư", "/materials"));
                shortcuts.add(new DashboardShortcut("Chứng từ nhập xuất", "/warehouse-transactions"));
                shortcuts.add(new DashboardShortcut("Điều chuyển kho", "/warehouse-transfers"));
                shortcuts.add(new DashboardShortcut("Báo cáo tồn kho", "/reports/detailed-inventory"));
            }
            case "INVENTORY_APPROVER" -> {
                shortcuts.add(new DashboardShortcut("Duyệt nhập xuất", "/transaction-approvals"));
                shortcuts.add(new DashboardShortcut("Duyệt điều chuyển", "/transfer-approvals"));
                shortcuts.add(new DashboardShortcut("Báo cáo tồn kho", "/reports/detailed-inventory"));
            }
            case "WAREHOUSE_KEEPER" -> {
                shortcuts.add(new DashboardShortcut("Quản lý kho", "/warehouses"));
                shortcuts.add(new DashboardShortcut("Xác nhận nhập xuất", "/transaction-confirmations"));
                shortcuts.add(new DashboardShortcut("Nhập xuất tồn", "/reports/nxt"));
                shortcuts.add(new DashboardShortcut("Thẻ kho", "/reports/stock-card"));
            }
            default -> {
                // Authorization guarantees one accepted role; keep the shared shortcuts only.
            }
        }
        return List.copyOf(shortcuts);
    }

    public record DashboardShortcut(String label, String path) {
    }
}
