package com.tttn.qlnvl.inventory.application;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyStockClosingJob {
    private final DailyStockClosingService closingService;
    private final Clock businessClock;

    public DailyStockClosingJob(DailyStockClosingService closingService, Clock businessClock) {
        this.closingService = closingService;
        this.businessClock = businessClock;
    }

    @Scheduled(cron = "${inventory.closing-cron:0 0 22 * * *}",
            zone = "${app.business-zone:Asia/Ho_Chi_Minh}")
    public void closeDailyInventory() {
        closingService.close(LocalDate.now(businessClock));
    }
}
