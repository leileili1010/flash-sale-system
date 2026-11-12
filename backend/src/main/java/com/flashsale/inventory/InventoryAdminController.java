package com.flashsale.inventory;

import com.flashsale.inventory.StockReconciliationService.DropStockReport;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (secured by path in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/inventory")
public class InventoryAdminController {

    private final StockReconciliationService reconciliation;

    InventoryAdminController(StockReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @GetMapping
    public List<DropStockReport> report() {
        return reconciliation.report();
    }

    @PostMapping("/{dropId}/resync")
    public DropStockReport resync(@PathVariable long dropId) {
        return reconciliation.resync(dropId);
    }
}
