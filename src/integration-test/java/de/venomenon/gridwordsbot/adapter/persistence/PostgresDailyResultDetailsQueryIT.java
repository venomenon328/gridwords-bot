package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.record.RecordScopeType;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresDailyResultDetailsQueryIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);
    private JdbcTemplate jdbc;
    private PostgresDailyStatusInteractionContextQuery contexts;
    private PostgresDailyResultDetailsQuery results;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        contexts = new PostgresDailyStatusInteractionContextQuery(jdbc);
        results = new PostgresDailyResultDetailsQuery(jdbc);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE player, daily_status_message RESTART IDENTITY CASCADE");
    }

    @Test
    void contextRequiresTheCurrentMessageAndQueriesEachHistoricalGameAudienceSeparately() {
        insertPlayer(1L, "Zulu");
        insertPlayer(2L, "alpha");
        insertPlayer(3L, "Bravo");
        insertPlayer(4L, "Inactive");
        insertPeriod(1L, GameType.GRIDWORDS, DATE.minusDays(2), null);
        insertPeriod(2L, GameType.QUADWORDS, DATE, DATE.plusDays(1));
        insertPeriod(3L, GameType.GRIDWORDS, DATE.minusDays(2), null);
        insertPeriod(3L, GameType.QUADWORDS, DATE.minusDays(2), null);
        insertPeriod(4L, GameType.GRIDWORDS, DATE.minusDays(10), DATE);
        insertStatus(11L, 12L, DATE, 99L);

        var current = contexts.findCurrent(11L, 12L, 99L, DATE).orElseThrow();

        assertThat(current.gridWordsParticipants())
                .extracting(participant -> participant.discordUserId() + ":" + participant.displayName())
                .containsExactly("3:Bravo", "1:Zulu");
        assertThat(current.quadWordsParticipants())
                .extracting(participant -> participant.discordUserId() + ":" + participant.displayName())
                .containsExactly("2:alpha", "3:Bravo");
        assertThat(contexts.findCurrent(11L, 12L, 100L, DATE)).isEmpty();
        assertThat(contexts.findCurrent(11L, 13L, 99L, DATE)).isEmpty();
        assertThat(contexts.findCurrent(10L, 12L, 99L, DATE)).isEmpty();
    }
    @Test
    void resultQueryReturnsTheCurrentCorrectedGridWordsRow() {
        insertPlayer(1L, "Player");
        jdbc.update("""
                INSERT INTO game_result
                    (player_id, game_type, game_date, solved, attempts_used, max_attempts,
                     duration_seconds, gridgames_streak, normalized_board, raw_share_text,
                     parser_version, created_at, updated_at)
                VALUES (?, 'GRIDWORDS', ?, TRUE, 3, 6, 85, 7, ?, 'old share',
                        'gridwords-share-v1', now(), now())
                """, 1L, DATE, "⬜⬜⬜⬜⬜\n🟨🟨🟨🟨🟨\n🟩🟩🟩🟩🟩");

        var initial = results.find(11L, 1L, GameType.GRIDWORDS, DATE).orElseThrow().result();
        assertThat(initial.outcome()).isEqualTo(new ShareOutcome.Solved(3, 6));
        assertThat(initial.duration()).isEqualTo(Duration.ofSeconds(85));
        assertThat(initial.board().orElseThrow().canonicalText()).contains("🟨🟨🟨🟨🟨");

        jdbc.update("""
                UPDATE game_result
                SET attempts_used = 2, duration_seconds = 61,
                    normalized_board = ?, raw_share_text = 'corrected share', updated_at = now()
                WHERE player_id = ? AND game_type = 'GRIDWORDS' AND game_date = ?
                """, "⬜⬜⬜⬜⬜\n🟩🟩🟩🟩🟩", 1L, DATE);

        var corrected = results.find(11L, 1L, GameType.GRIDWORDS, DATE).orElseThrow().result();
        assertThat(corrected.outcome()).isEqualTo(new ShareOutcome.Solved(2, 6));
        assertThat(corrected.duration()).isEqualTo(Duration.ofSeconds(61));
        assertThat(corrected.board().orElseThrow().rows()).containsExactly(
                "⬜⬜⬜⬜⬜", "🟩🟩🟩🟩🟩");
    }

    @Test
    void resultQueryReadsOnlyCurrentRecordAndActiveAchievementProjections() {
        insertPlayer(1L, "Player");
        jdbc.update("""
                INSERT INTO game_result
                    (player_id, game_type, game_date, solved, attempts_used, max_attempts,
                     duration_seconds, normalized_board, raw_share_text, parser_version, created_at, updated_at)
                VALUES (1, 'GRIDWORDS', ?, TRUE, 3, 6, 85, ?, 'share', 'gridwords-share-v1', now(), now())
                """, DATE, String.join("\n", List.of("⬜⬜⬜⬜⬜", "🟨🟨🟨🟨🟨", "🟩🟩🟩🟩🟩")));
        Long resultId = jdbc.queryForObject("SELECT id FROM game_result WHERE player_id=1 AND game_date=?", Long.class, DATE);
        jdbc.update("""
                INSERT INTO record_state (
                    guild_id, definition_key, definition_version, scope_type, scope_key, holder_player_id,
                    value_kind, attempts, duration_millis, source_type, source_game_result_id,
                    source_game_result_version, source_game_player_id, source_game_type, source_game_date,
                    running, lock_version, created_at, updated_at)
                VALUES (11, 'result.gridwords.fewest-attempts.personal', 'records-v1', 'PERSONAL', '1', 1,
                    'ATTEMPTS_AND_DURATION', 3, 85000, 'GAME_RESULT', ?, 0, 1, 'GRIDWORDS', ?,
                    FALSE, 0, now(), now())
                """, resultId, DATE);
        insertAward("participation.1.gridwords", "ACTIVE", null);
        insertAward("participation.1.quadwords", "INVALIDATED", "now()");
        int before = jdbc.queryForObject("SELECT count(*) FROM record_state", Integer.class)
                + jdbc.queryForObject("SELECT count(*) FROM achievement_award_state", Integer.class);

        var details = results.find(11L, 1L, GameType.GRIDWORDS, DATE).orElseThrow();

        assertThat(details.currentRecords()).containsExactly(new de.venomenon.gridwordsbot.port.out.DailyResultDetailsQuery.CurrentRecord(
                "result.gridwords.fewest-attempts.personal", RecordScopeType.PERSONAL));
        assertThat(details.activeAchievementKeys()).containsExactly("participation.1.gridwords");
        int after = jdbc.queryForObject("SELECT count(*) FROM record_state", Integer.class)
                + jdbc.queryForObject("SELECT count(*) FROM achievement_award_state", Integer.class);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void resultQueryDistinguishesFourBoardsBoardlessAndMissingQuadWords() {
        insertPlayer(1L, "With boards");
        insertPlayer(2L, "Boardless");
        insertPlayer(3L, "Missing");
        String topLeft = board("⬜⬜⬜⬜⬜");
        String topRight = board("🟨⬜⬜⬜⬜");
        String bottomLeft = board("⬜🟨⬜⬜⬜");
        String bottomRight = board("⬜⬜🟨⬜⬜");
        jdbc.update("""
                INSERT INTO game_result
                    (player_id, game_type, game_date, solved, attempts_used, max_attempts,
                     duration_seconds, normalized_board, raw_share_text, parser_version,
                     quadwords_top_left_board, quadwords_top_right_board,
                     quadwords_bottom_left_board, quadwords_bottom_right_board,
                     created_at, updated_at)
                VALUES (?, 'QUADWORDS', ?, TRUE, 3, 9, 90, NULL, 'share', 'quadwords-image-v2',
                        ?, ?, ?, ?, now(), now())
                """, 1L, DATE, topLeft, topRight, bottomLeft, bottomRight);
        jdbc.update("""
                INSERT INTO game_result
                    (player_id, game_type, game_date, solved, attempts_used, max_attempts,
                     duration_seconds, normalized_board, raw_share_text, parser_version,
                     created_at, updated_at)
                VALUES (?, 'QUADWORDS', ?, TRUE, 4, 9, 100, NULL, 'share',
                        'quadwords-share-v1', now(), now())
                """, 2L, DATE);

        var withBoards = results.find(11L, 1L, GameType.QUADWORDS, DATE).orElseThrow().result();
        var boardless = results.find(11L, 2L, GameType.QUADWORDS, DATE).orElseThrow().result();

        assertThat(withBoards.quadWordsBoards()).isPresent();
        assertThat(withBoards.quadWordsBoards().orElseThrow().ordered())
                .extracting(board -> board.canonicalText())
                .containsExactly(topLeft, topRight, bottomLeft, bottomRight);
        assertThat(boardless.quadWordsBoards()).isEmpty();
        assertThat(boardless.outcome()).isEqualTo(new ShareOutcome.Solved(4, 9));
        assertThat(results.find(11L, 3L, GameType.QUADWORDS, DATE)).isEmpty();
    }

    private void insertPlayer(long id, String displayName) {
        jdbc.update("""
                INSERT INTO player
                    (discord_user_id, display_name, active, administrator, created_at, updated_at)
                VALUES (?, ?, TRUE, FALSE, now(), now())
                """, id, displayName);
    }

    private void insertAward(String key, String status, String invalidatedAt) {
        if (invalidatedAt == null) {
            jdbc.update("""
                    INSERT INTO achievement_award_state
                        (guild_id, participant_id, achievement_key, definition_version, award_status, earned_on,
                         detected_at, evidence_kind, evidence_reference, invalidated_at, lock_version, created_at, updated_at)
                    VALUES (11, 1, ?, 'achievements-v1', ?, ?, now(), 'GAME_RESULT', 'test', NULL, 0, now(), now())
                    """, key, status, DATE);
        } else {
            jdbc.update("""
                    INSERT INTO achievement_award_state
                        (guild_id, participant_id, achievement_key, definition_version, award_status, earned_on,
                         detected_at, evidence_kind, evidence_reference, invalidated_at, lock_version, created_at, updated_at)
                    VALUES (11, 1, ?, 'achievements-v1', ?, ?, now(), 'GAME_RESULT', 'test', now(), 0, now(), now())
                    """, key, status, DATE);
        }
    }

    private void insertPeriod(long playerId, GameType gameType, LocalDate activeFrom, LocalDate inactiveFrom) {
        jdbc.update("""
                INSERT INTO player_participation_period
                    (player_id, game_type, active_from, inactive_from, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                """, playerId, gameType.name(), activeFrom, inactiveFrom);
    }
    private void insertStatus(long guildId, long channelId, LocalDate date, long messageId) {
        jdbc.update("""
                INSERT INTO daily_status_message
                    (guild_id, channel_id, game_date, bot_message_id, delivery_state,
                     content_fingerprint, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'DELIVERED', 'fingerprint', now(), now())
                """, guildId, channelId, date, messageId);
    }

    private static String board(String firstRow) {
        return String.join("\n", List.of(firstRow, "🟨🟨🟨🟨🟨", "🟩🟩🟩🟩🟩"));
    }
}
