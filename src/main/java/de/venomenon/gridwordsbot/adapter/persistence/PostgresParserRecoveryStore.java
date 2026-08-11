package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.port.out.ParserRecoveryStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL implementation of the narrow parser-repair recovery boundary. */
@Repository
@Profile("database")
public final class PostgresParserRecoveryStore implements ParserRecoveryStore {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostgresParserRecoveryStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public List<Candidate> findCandidates(long guildId, long channelId, ParseErrorCode errorCode) {
        requirePositive(guildId, "guildId");
        requirePositive(channelId, "channelId");
        Objects.requireNonNull(errorCode, "errorCode");
        return jdbc.query("""
                SELECT source_message_id, raw_message_content, processing_state
                FROM submission
                WHERE guild_id = ? AND channel_id = ? AND parser_error_code = ?
                  AND (
                      processing_state IN (
                          'PARSE_REJECTED', 'RECEIVED', 'RESULT_STORED', 'CANONICAL_MESSAGE_PUBLISHED',
                          'ORIGINAL_MESSAGE_DELETED', 'COMPLETED', 'SUPERSEDED')
                      OR (processing_state = 'FAILED_RETRYABLE' AND game_result_id IS NOT NULL)
                  )
                ORDER BY received_at, source_message_id
                """, (rs, row) -> new Candidate(
                        rs.getLong("source_message_id"),
                        rs.getString("raw_message_content"),
                        SubmissionStore.SubmissionState.valueOf(rs.getString("processing_state"))),
                guildId, channelId, errorCode.name());
    }

    @Override
    @Transactional
    public boolean prepare(long sourceMessageId, ParseErrorCode errorCode) {
        requirePositive(sourceMessageId, "sourceMessageId");
        Objects.requireNonNull(errorCode, "errorCode");
        int changed = jdbc.update("""
                UPDATE submission
                SET processing_state = 'RECEIVED', technical_error_message = NULL,
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ? AND parser_error_code = ?
                  AND processing_state = 'PARSE_REJECTED'
                """, databaseTime(), sourceMessageId, errorCode.name());
        if (changed == 1) {
            return true;
        }
        Integer prepared = jdbc.queryForObject("""
                SELECT count(*)
                FROM submission
                WHERE source_message_id = ? AND parser_error_code = ? AND processing_state = 'RECEIVED'
                """, Integer.class, sourceMessageId, errorCode.name());
        return prepared != null && prepared == 1;
    }

    @Override
    @Transactional
    public boolean complete(long sourceMessageId, ParseErrorCode errorCode) {
        requirePositive(sourceMessageId, "sourceMessageId");
        Objects.requireNonNull(errorCode, "errorCode");
        return jdbc.update("""
                UPDATE submission
                SET parser_error_code = NULL, technical_error_message = NULL,
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ? AND parser_error_code = ?
                  AND processing_state IN (
                      'RESULT_STORED', 'CANONICAL_MESSAGE_PUBLISHED', 'ORIGINAL_MESSAGE_DELETED',
                      'COMPLETED', 'SUPERSEDED')
                """, databaseTime(), sourceMessageId, errorCode.name()) == 1;
    }

    private OffsetDateTime databaseTime() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
