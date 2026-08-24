package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.achievement.AchievementCatalogQueryService;
import de.venomenon.gridwordsbot.application.achievement.AchievementsQueryService;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.AchievementScope;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.port.in.AchievementCatalogQueryUseCase;
import de.venomenon.gridwordsbot.port.in.AchievementsQueryUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
class PostgresAchievementsQueryIT {
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PostgresAchievementAwardStateStore awards;
    private PostgresRecordEventStore records;
    private AchievementDefinitionCatalog catalog;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        awards = new PostgresAchievementAwardStateStore(jdbc, CLOCK);
        records = new PostgresRecordEventStore(jdbc, CLOCK);
        catalog = AchievementDefinitionCatalog.achievementsV2();
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE record_event, achievement_announcement_item, achievement_announcement, achievement_event, "
                + "achievement_award_state, achievement_bootstrap_state, player CASCADE");
    }

    @Test
    void readsOnlyActiveMaterializedAwardsAndDoesNotCreateOrChangePlayers() {
        var gridDefinitions = catalog.definitions().stream()
                .filter(definition -> definition.scope() == AchievementScope.GRIDWORDS)
                .limit(2)
                .toList();
        awards.initialize(
                new AchievementAwardState.Key(1, 77, gridDefinitions.get(0).key()),
                write(AchievementAwardState.Status.ACTIVE, Optional.empty(), "result:active"));
        awards.initialize(
                new AchievementAwardState.Key(1, 77, gridDefinitions.get(1).key()),
                write(AchievementAwardState.Status.INVALIDATED, Optional.of(NOW), "result:invalidated"));

        AchievementsQueryService service = new AchievementsQueryService(awards, catalog);
        var result = service.query(new AchievementsQueryUseCase.Query(
                1, 77, AchievementsQueryUseCase.GameFilter.GRIDWORDS));
        var emptyForeignProfile = service.query(new AchievementsQueryUseCase.Query(
                1, 88, AchievementsQueryUseCase.GameFilter.ALL));

        assertThat(result.entries()).extracting(entry -> entry.key())
                .containsExactly(gridDefinitions.get(0).key());
        assertThat(emptyForeignProfile.entries()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_event", Integer.class)).isZero();
    }

    @Test
    void fullCatalogQueryReturnsAllSixtyTwoAndMarksOnlyActiveStateWithoutSideEffects() {
        var first = catalog.definitions().get(0);
        var second = catalog.definitions().get(1);
        awards.initialize(
                new AchievementAwardState.Key(1, 77, first.key()),
                write(AchievementAwardState.Status.ACTIVE, Optional.empty(), "result:active"));
        awards.initialize(
                new AchievementAwardState.Key(1, 77, second.key()),
                write(AchievementAwardState.Status.INVALIDATED, Optional.of(NOW), "result:invalidated"));

        var result = new AchievementCatalogQueryService(awards, catalog)
                .query(new AchievementCatalogQueryUseCase.Query(1, 77));

        assertThat(result.entries()).hasSize(62);
        assertThat(result.entries().get(0).achieved()).isTrue();
        assertThat(result.entries().get(1).achieved()).isFalse();
        assertThat(result.entries().stream().skip(2)).allMatch(entry -> !entry.achieved());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_event", Integer.class)).isZero();
    }

    @Test
    void readsCurrentAwardsAndCanonicalRecordDatesForOneReportPeriodWithoutWrites() {
        var definitions = catalog.definitions();
        awards.initialize(new AchievementAwardState.Key(1, 77, definitions.get(0).key()),
                write(AchievementAwardState.Status.ACTIVE, Optional.empty(), "period-active", LocalDate.of(2026, 8, 2)));
        awards.initialize(new AchievementAwardState.Key(1, 77, definitions.get(1).key()),
                write(AchievementAwardState.Status.INVALIDATED, Optional.of(NOW), "period-invalid", LocalDate.of(2026, 8, 2)));
        awards.initialize(new AchievementAwardState.Key(1, 88, definitions.get(2).key()),
                write(AchievementAwardState.Status.ACTIVE, Optional.empty(), "outside", LocalDate.of(2026, 7, 26)));
        records.append(record("00000000-0000-0000-0000-000000000101", "period-record", 77, LocalDate.of(2026, 8, 2),
                RecordEventType.RESULT_RECORD_BROKEN, RecordProcessingOrigin.DAY_CLOSE));
        records.append(record("00000000-0000-0000-0000-000000000102", "old-record", 77, LocalDate.of(2026, 7, 26),
                RecordEventType.RESULT_RECORD_BROKEN, RecordProcessingOrigin.LIVE_SUBMISSION));
        records.append(record("00000000-0000-0000-0000-000000000103", "silent-record", 77, LocalDate.of(2026, 8, 2),
                RecordEventType.RESULT_RECORD_BROKEN, RecordProcessingOrigin.BACKFILL));
        records.append(record("00000000-0000-0000-0000-000000000104", "tie-record", 77, LocalDate.of(2026, 8, 2),
                RecordEventType.SERIES_RECORD_TIED_AT_END, RecordProcessingOrigin.DAY_CLOSE));
        UUID invalidatedEvent = UUID.fromString("00000000-0000-0000-0000-000000000105");
        records.append(record(invalidatedEvent.toString(), "invalidated-record", 77, LocalDate.of(2026, 8, 2),
                RecordEventType.RESULT_RECORD_BROKEN, RecordProcessingOrigin.LIVE_SUBMISSION));
        assertThat(records.invalidate(invalidatedEvent, NOW.plusSeconds(1))).isTrue();

        var facts = new PostgresReportHighlightQuery(awards, records).find(1,
                new ReportPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2)), Set.of(77L, 88L));

        assertThat(facts.activeAwardsByParticipant()).containsExactlyEntriesOf(java.util.Map.of(77L, 1));
        assertThat(facts.recordEvents()).extracting(event -> event.draft().idempotencyKey()).containsExactly("period-record");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_event", Integer.class)).isZero();
    }

    private AchievementAwardState.Write write(
            AchievementAwardState.Status status, Optional<Instant> invalidatedAt, String evidenceReference) {
        return write(status, invalidatedAt, evidenceReference, LocalDate.of(2026, 8, 7));
    }

    private AchievementAwardState.Write write(
            AchievementAwardState.Status status, Optional<Instant> invalidatedAt, String evidenceReference, LocalDate earnedOn) {
        return new AchievementAwardState.Write(
                catalog.version(), status, earnedOn, NOW,
                AchievementEvidence.Kind.GAME_RESULT, evidenceReference, invalidatedAt);
    }

    private static RecordEventDraft record(String eventId, String key, long playerId, LocalDate gameDate,
            RecordEventType type, RecordProcessingOrigin origin) {
        return new RecordEventDraft(UUID.fromString(eventId), key,
                new RecordStateKey(1, new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
                        RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(playerId)), type, Optional.empty(),
                new AttemptsDurationRecordValue(4, Duration.ofSeconds(98)), Optional.empty(), Optional.of(playerId),
                Optional.empty(), new RecordSourceReference.GameResult(101, 0, playerId, GameType.GRIDWORDS, gameDate),
                key, origin, NOW);
    }
}
