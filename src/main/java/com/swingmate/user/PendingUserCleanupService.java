package com.swingmate.user;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 오래된 가입 미완료 계정과 그 계정에만 속한 임시 데이터를 배치 삭제한다. */
@Service
public class PendingUserCleanupService {

    public static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;

    public PendingUserCleanupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int deleteExpiredBatch(Instant cutoff) {
        List<UUID> userIds = jdbc.query(
                "select id from users "
                        + "where onboarding_status = 'PENDING' and updated_at < ? "
                        + "order by updated_at limit ? for update",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                Timestamp.from(cutoff), BATCH_SIZE);

        int deletedUsers = 0;
        for (UUID userId : userIds) {
            deleteRelatedRows(userId);
            deletedUsers += jdbc.update(
                    "delete from users where id = ? and onboarding_status = 'PENDING' and updated_at < ?",
                    userId, Timestamp.from(cutoff));
        }
        return deletedUsers;
    }

    private void deleteRelatedRows(UUID userId) {
        // 종속 테이블을 먼저 지워 외래 키 제약을 지킨다. PENDING 계정에만 연결된 행이다.
        jdbc.update("delete from member_lesson_interests where user_id = ?", userId);
        jdbc.update("delete from pro_careers where user_id = ?", userId);
        jdbc.update("delete from pro_availability where user_id = ?", userId);
        jdbc.update("delete from connection_codes where member_id = ?", userId);
        jdbc.update("delete from pro_member_connections where pro_id = ? or member_id = ?", userId, userId);
        jdbc.update("delete from member_profiles where user_id = ?", userId);
        jdbc.update("delete from pro_profiles where user_id = ?", userId);
        jdbc.update("delete from phone_verifications where user_id = ?", userId);
        jdbc.update("delete from refresh_tokens where user_id = ?", userId);
        jdbc.update("delete from social_accounts where user_id = ?", userId);
    }
}
