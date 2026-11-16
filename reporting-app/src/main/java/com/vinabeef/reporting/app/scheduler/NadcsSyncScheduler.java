package com.vinabeef.reporting.app.scheduler;

import com.vinabeef.reporting.kernel.api.ReportingFacade;
import com.vinabeef.reporting.kernel.model.DateRange;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Daily batch transfer to NADCS (FR-12). Failures are alerted inside the kernel. */
@Component
public class NadcsSyncScheduler {

    private final ReportingFacade facade;
    private final Clock clock;

    public NadcsSyncScheduler(ReportingFacade facade, Clock clock) {
        this.facade = facade;
        this.clock = clock;
    }

    @Scheduled(cron = "${reporting.nadcs.cron:0 0 1 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void runDailySync() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        facade.sync("NADCS", DateRange.singleDay(yesterday));
    }
}
