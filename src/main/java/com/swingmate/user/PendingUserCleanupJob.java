package com.swingmate.user;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 새벽 3시에 30일간 가입 활동이 없는 PENDING 계정을 정리한다. */
@Component
public class PendingUserCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PendingUserCleanupJob.class);
    private static final Duration INACTIVITY_TTL = Duration.ofDays(30);

    private final PendingUserCleanupService cleanupService;

    public PendingUserCleanupJob(PendingUserCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void cleanupExpiredPendingUsers() {
        Instant cutoff = Instant.now().minus(INACTIVITY_TTL);
        int batchDeleted;
        int totalDeleted = 0;
        do {
            batchDeleted = cleanupService.deleteExpiredBatch(cutoff);
            totalDeleted += batchDeleted;
        } while (batchDeleted == PendingUserCleanupService.BATCH_SIZE);

        if (totalDeleted > 0) {
            log.info("Removed {} inactive pending accounts", totalDeleted);
        }
    }
}
