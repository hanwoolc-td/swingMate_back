package com.swingmate.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PendingUserCleanupTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PendingUserCleanupService cleanupService;

    @Test
    void removesOnlyStalePendingUsersAndTheirDependentData() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofDays(30));
        UUID staleUserId = createUser(OnboardingStatus.PENDING, cutoff.minusSeconds(1));
        UUID completedUserId = createUser(OnboardingStatus.COMPLETED, cutoff.minusSeconds(1));
        UUID recentPendingUserId = createUser(OnboardingStatus.PENDING, now);
        addSocialAccount(staleUserId);
        addVerification(staleUserId, now);

        int deleted = cleanupService.deleteExpiredBatch(cutoff);

        assertEquals(1, deleted);
        assertEquals(0, count("users", staleUserId));
        assertEquals(0, count("social_accounts", staleUserId));
        assertEquals(0, count("phone_verifications", staleUserId));
        assertEquals(1, count("users", completedUserId));
        assertEquals(1, count("users", recentPendingUserId));
    }

    private UUID createUser(OnboardingStatus status, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        Timestamp timestamp = Timestamp.from(updatedAt);
        jdbc.update("insert into users (id, role, onboarding_status, created_at, updated_at) values (?, ?, ?, ?, ?)",
                id, status == OnboardingStatus.COMPLETED ? "MEMBER" : null,
                status.name(), timestamp, timestamp);
        return id;
    }

    private void addSocialAccount(UUID userId) {
        jdbc.update("insert into social_accounts (id, user_id, provider, provider_user_id, connected_at) "
                        + "values (?, ?, 'GOOGLE', ?, ?)",
                UUID.randomUUID(), userId, "cleanup-" + userId, Timestamp.from(Instant.now()));
    }

    private void addVerification(UUID userId, Instant now) {
        jdbc.update("insert into phone_verifications "
                        + "(id, user_id, phone_number, purpose, code_hash, expires_at, attempt_count, resend_count, created_at) "
                        + "values (?, ?, ?, 'SIGNUP_PHONE_VERIFICATION', ?, ?, 0, 0, ?)",
                UUID.randomUUID(), userId, "01012345678", "test-hash",
                Timestamp.from(now.plusSeconds(180)), Timestamp.from(now));
    }

    private int count(String table, UUID userId) {
        return jdbc.queryForObject("select count(*) from " + table + " where "
                + (table.equals("users") ? "id" : "user_id") + " = ?", Integer.class, userId);
    }
}
