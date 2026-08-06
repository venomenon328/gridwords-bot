package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.port.out.SourceDeletionRecoveryStore;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Reactivates permanent source-delete failures only at explicit recovery boundaries. */
@Repository
@Profile("database")
public class PostgresSourceDeletionRecoveryAdapter implements SourceDeletionRecoveryStore {

    private static final String BASE_UPDATE = """
            UPDATE submission s
            SET source_delete_failure_class = 'NONE',
                technical_error_message = NULL,
                updated_at = ?,
                version = s.version + 1
            FROM game_result r
            WHERE s.game_result_id = r.id
              AND s.source_delete_failure_class = 'PERMANENT'
              AND s.processing_state IN ('CANONICAL_MESSAGE_PUBLISHED', 'SUPERSEDED')
              AND r.canonical_message_id IS NOT NULL
              AND r.canonical_message_id <> s.source_message_id
              AND (
                r.game_type = 'GRIDWORDS'
                OR (
                    r.game_type = 'QUADWORDS'
                    AND r.quadwords_top_left_board IS NOT NULL
                    AND r.quadwords_top_right_board IS NOT NULL
                    AND r.quadwords_bottom_left_board IS NOT NULL
                    AND r.quadwords_bottom_right_board IS NOT NULL
                )
              )
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostgresSourceDeletionRecoveryAdapter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int reactivatePermanentFailures(OptionalLong gameResultId) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (gameResultId.isPresent()) {
            return jdbc.update(BASE_UPDATE + " AND r.id = ?", now, gameResultId.getAsLong());
        }
        return jdbc.update(BASE_UPDATE, now);
    }

    @Override
    public List<Long> findPermanentlyFailedResultIds() {
        return jdbc.queryForList("""
                SELECT DISTINCT s.game_result_id
                FROM submission s
                JOIN game_result r ON r.id = s.game_result_id
                WHERE s.source_delete_failure_class = 'PERMANENT'
                  AND s.processing_state IN ('CANONICAL_MESSAGE_PUBLISHED', 'SUPERSEDED')
                  AND r.canonical_message_id IS NOT NULL
                  AND r.canonical_message_id <> s.source_message_id
                  AND (
                    r.game_type = 'GRIDWORDS'
                    OR (
                        r.game_type = 'QUADWORDS'
                        AND r.quadwords_top_left_board IS NOT NULL
                        AND r.quadwords_top_right_board IS NOT NULL
                        AND r.quadwords_bottom_left_board IS NOT NULL
                        AND r.quadwords_bottom_right_board IS NOT NULL
                    )
                  )
                ORDER BY s.game_result_id
                """, Long.class);
    }
}
