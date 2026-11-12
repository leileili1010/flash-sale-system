package com.flashsale.inventory;

import com.flashsale.inventory.StockReconciliationService.DropStockReport;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Logs Redis/database inventory drift. Correction is a deliberate admin action, never automatic. */
@Component
class StockReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(StockReconciliationJob.class);

    private final StockReconciliationService reconciliation;

    StockReconciliationJob(StockReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(
            initialDelayString = "${flashsale.inventory-reconciliation.interval}",
            fixedDelayString = "${flashsale.inventory-reconciliation.interval}")
    @SchedulerLock(name = "inventory-reconciliation", lockAtMostFor = "PT5M")
    void reconcile() {
        for (DropStockReport report : reconciliation.report()) {
            if (!report.consistent()) {
                log.warn("Inventory drift on drop {}: redis={} databaseAvailable={} inFlight={} drift={}",
                        report.dropId(), report.redisStock(), report.databaseAvailable(),
                        report.inFlightReservations(), report.drift());
            }
        }
    }
}
