package de.venomenon.gridwordsbot.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.port.out.ParserRecoveryStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
class PostgresParserRecoveryStoreIT {

    private static final long GUILD_ID = 9127L;
    private static final long CHANNEL_ID = 8127L;
    private static final long PLAYER_ID = 7127L;
    private static final Instant NOW = Instant.parse("2026-08-11T19:36:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private PostgresPersistenceAdapter submissions;
    private PostgresParserRecoveryStore recovery;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        submissions = new PostgresPersistenceAdapter(jdbc, clock);
        recovery = new PostgresParserRecoveryStore(jdbc, clock);
        submissions.upsert(new PlayerStore.PlayerUpsert(PLAYER_ID, "Recovery Player", true, false));
    }

    @Test
    void preparesRejectedShareIdempotentlyAndKeepsItDiscoverableAcrossRestart() {
        long sourceMessageId = 612701L;
        registerAndReject(sourceMessageId, ParseErrorCode.INVALID_DURATION);
        registerAndReject(612702L, ParseErrorCode.INVALID_DATE);

        List<ParserRecoveryStore.Candidate> candidates = recovery.findCandidates(
                GUILD_ID, CHANNEL_ID, ParseErrorCode.INVALID_DURATION);
        assertEquals(List.of(sourceMessageId), candidates.stream()
                .map(ParserRecoveryStore.Candidate::sourceMessageId)
                .toList());
        assertEquals(SubmissionStore.SubmissionState.PARSE_REJECTED, candidates.getFirst().state());

        assertTrue(recovery.prepare(sourceMessageId, ParseErrorCode.INVALID_DURATION));
        assertTrue(recovery.prepare(sourceMessageId, ParseErrorCode.INVALID_DURATION));
        SubmissionStore.StoredSubmission prepared = submissions.findBySourceMessageId(sourceMessageId).orElseThrow();
        assertEquals(SubmissionStore.SubmissionState.RECEIVED, prepared.state());
        assertEquals(Optional.of(ParseErrorCode.INVALID_DURATION.name()), prepared.parserErrorCode());

        List<ParserRecoveryStore.Candidate> afterRestart = recovery.findCandidates(
                GUILD_ID, CHANNEL_ID, ParseErrorCode.INVALID_DURATION);
        assertEquals(List.of(sourceMessageId), afterRestart.stream()
                .map(ParserRecoveryStore.Candidate::sourceMessageId)
                .toList());
        assertEquals(SubmissionStore.SubmissionState.RECEIVED, afterRestart.getFirst().state());
    }

    @Test
    void clearsMarkerOnlyAfterDurablePostParseState() {
        long sourceMessageId = 612703L;
        registerAndReject(sourceMessageId, ParseErrorCode.INVALID_DURATION);
        assertTrue(recovery.prepare(sourceMessageId, ParseErrorCode.INVALID_DURATION));
        assertFalse(recovery.complete(sourceMessageId, ParseErrorCode.INVALID_DURATION));

        assertTrue(submissions.transition(
                sourceMessageId,
                SubmissionStore.SubmissionState.RECEIVED,
                SubmissionStore.SubmissionState.SUPERSEDED));
        assertTrue(recovery.complete(sourceMessageId, ParseErrorCode.INVALID_DURATION));

        SubmissionStore.StoredSubmission completed = submissions.findBySourceMessageId(sourceMessageId).orElseThrow();
        assertEquals(SubmissionStore.SubmissionState.SUPERSEDED, completed.state());
        assertTrue(completed.parserErrorCode().isEmpty());
        assertTrue(recovery.findCandidates(GUILD_ID, CHANNEL_ID, ParseErrorCode.INVALID_DURATION).stream()
                .noneMatch(candidate -> candidate.sourceMessageId() == sourceMessageId));
    }

    private void registerAndReject(long sourceMessageId, ParseErrorCode errorCode) {
        submissions.register(new SubmissionStore.SubmissionRegistration(
                sourceMessageId,
                GUILD_ID,
                CHANNEL_ID,
                PLAYER_ID,
                "GridWords (11. August 2026) X/6 in 7:38:28",
                List.of(),
                NOW));
        submissions.reject(new SubmissionStore.RejectedSubmission(sourceMessageId, errorCode.name()));
    }
}
