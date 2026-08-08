package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.achievement.AchievementsQueryService;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.AchievementScope;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.port.in.AchievementsQueryUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
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
        catalog = AchievementDefinitionCatalog.achievementsV1();
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE achievement_announcement_item, achievement_announcement, achievement_event, "
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

    private AchievementAwardState.Write write(
            AchievementAwardState.Status status, Optional<Instant> invalidatedAt, String evidenceReference) {
        return new AchievementAwardState.Write(
                catalog.version(), status, LocalDate.of(2026, 8, 7), NOW,
                AchievementEvidence.Kind.GAME_RESULT, evidenceReference, invalidatedAt);
    }
}
