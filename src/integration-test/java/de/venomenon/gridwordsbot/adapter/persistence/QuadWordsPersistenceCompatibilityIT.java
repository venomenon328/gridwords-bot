package de.venomenon.gridwordsbot.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.venomenon.gridwordsbot.application.submission.ProcessSharedResultService;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentReference;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsImageParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
import de.venomenon.gridwordsbot.port.out.AttachmentContentLoader;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuadWordsPersistenceCompatibilityIT {

    private static final Instant NOW = Instant.parse("2026-07-29T08:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private LegacyCompatiblePostgresPersistenceAdapter adapter;
    private JdbcTemplate jdbc;

    @BeforeAll
    void migrateLegacyDatabase() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/legacy-quadwords-upgrade-test.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        adapter = new LegacyCompatiblePostgresPersistenceAdapter(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void migrationPreservesARealVersionOneQuadWordsRowWithoutInventingBoards() {
        var legacy = adapter.find(9001L, GameType.QUADWORDS, LocalDate.of(2026, 7, 28)).orElseThrow();

        assertEquals("quadwords-share-v1", legacy.parserVersion());
        assertTrue(legacy.parsedResult().quadWordsBoards().isEmpty());
        assertEquals(7, ((ShareOutcome.Solved) legacy.parsedResult().outcome()).attemptsUsed());
    }

    @Test
    void firstImageBackedCorrectionUpgradesTheExistingLegacyResultInPlace() {
        long playerId = 9003L;
        long sourceMessageId = 9903L;
        LocalDate gameDate = LocalDate.of(2026, 7, 28);
        insertLegacyResult(playerId, gameDate);
        long resultIdBefore = jdbc.queryForObject(
                "select id from game_result where player_id = ? and game_type = 'QUADWORDS'",
                Long.class,
                playerId);
        ProcessSharedResultService service = service(attachment -> new byte[] {1}, parsedBoards(7));

        ProcessingResult result = service.process(message(
                sourceMessageId,
                playerId,
                "QuadWords (28. Juli 2026) 7/9 in 4:05"));

        assertEquals(new ProcessingResult.Accepted(GameType.QUADWORDS), result);
        var upgraded = adapter.find(playerId, GameType.QUADWORDS, gameDate).orElseThrow();
        assertEquals(resultIdBefore, upgraded.id());
        assertEquals(QuadWordsImageParser.VERSION, upgraded.parserVersion());
        assertTrue(upgraded.parsedResult().quadWordsBoards().isPresent());
        assertEquals(SubmissionStore.SubmissionState.RESULT_STORED,
                adapter.findBySourceMessageId(sourceMessageId).orElseThrow().state());
    }

    @Test
    void preResultDownloadFailureSurvivesPostgresRoundTripAndCompletesOnReplay() {
        long playerId = 9002L;
        long sourceMessageId = 9902L;
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Retry", true, false));
        AtomicInteger loads = new AtomicInteger();
        AttachmentContentLoader loader = attachment -> {
            if (loads.getAndIncrement() == 0) {
                throw new AttachmentContentLoader.RetryableAttachmentException("network", null);
            }
            return new byte[] {1};
        };
        ProcessSharedResultService service = service(loader, parsedBoards(7));
        InboundSharedMessage inbound = message(
                sourceMessageId,
                playerId,
                "QuadWords (29. Juli 2026) 7/9 in 4:05");

        assertEquals(new ProcessingResult.Ignored(), service.process(inbound));
        SubmissionStore.StoredSubmission failed = adapter.findBySourceMessageId(sourceMessageId).orElseThrow();
        assertEquals(SubmissionStore.SubmissionState.FAILED_RETRYABLE, failed.state());
        assertTrue(failed.gameResultId().isEmpty());
        assertEquals(Optional.of("attachment download failed"), failed.technicalErrorMessage());
        assertFalse(adapter.find(playerId, GameType.QUADWORDS, LocalDate.of(2026, 7, 29)).isPresent());

        assertEquals(new ProcessingResult.Accepted(GameType.QUADWORDS), service.process(inbound));
        SubmissionStore.StoredSubmission completed = adapter.findBySourceMessageId(sourceMessageId).orElseThrow();
        assertEquals(SubmissionStore.SubmissionState.RESULT_STORED, completed.state());
        assertTrue(completed.gameResultId().isPresent());
        assertTrue(adapter.find(playerId, GameType.QUADWORDS, LocalDate.of(2026, 7, 29))
                .orElseThrow().parsedResult().quadWordsBoards().isPresent());
        assertEquals(2, loads.get());
    }

    private void insertLegacyResult(long playerId, LocalDate gameDate) {
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Legacy correction", true, false));
        jdbc.update("""
                insert into game_result(
                    player_id, game_type, game_date, solved, attempts_used, max_attempts,
                    duration_seconds, normalized_board, raw_share_text, parser_version,
                    created_at, updated_at)
                values (?, 'QUADWORDS', ?, true, 7, 9, 245, null, 'legacy share',
                    'quadwords-share-v1', ?, ?)
                """,
                playerId,
                gameDate,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    private ProcessSharedResultService service(
            AttachmentContentLoader loader,
            QuadWordsImageParser.Parse parserResult) {
        QuadWordsImageParser parser = new QuadWordsImageParser() {
            @Override
            public Parse parse(byte[] bytes, ShareOutcome outcome) {
                return parserResult;
            }
        };
        return new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                loader,
                parser,
                Clock.fixed(NOW, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                adapter,
                adapter, ignored -> true);
    }

    private static InboundSharedMessage message(
            long sourceMessageId,
            long playerId,
            String content) {
        return new InboundSharedMessage(
                200L,
                300L,
                sourceMessageId,
                playerId,
                "Player",
                content,
                List.of(new AttachmentMetadata(
                        "quadwords.png",
                        "image/png",
                        100L,
                        Optional.of(new AttachmentReference(300L, sourceMessageId, 700L)))),
                NOW);
    }

    private static QuadWordsImageParser.Parse parsedBoards(int rows) {
        return new QuadWordsImageParser.Parse.Parsed(boards(rows));
    }

    private static QuadWordsBoards boards(int rows) {
        String line = "⬜".repeat(5);
        QuadWordsBoard board = new QuadWordsBoard(Collections.nCopies(rows, line));
        return new QuadWordsBoards(board, board, board, board);
    }
}
