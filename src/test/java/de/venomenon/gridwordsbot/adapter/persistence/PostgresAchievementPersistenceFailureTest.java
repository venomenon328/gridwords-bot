package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionVersion;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresAchievementPersistenceFailureTest {
    @Test
    void unknownJdbcFailureEscapesEventAppendUnchanged() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database mapping exploded");
        JdbcTemplate brokenJdbc = new JdbcTemplate() {
            @Override
            public int update(String sql, Object... args) {
                throw failure;
            }
        };
        PostgresAchievementEventStore store = new PostgresAchievementEventStore(
                brokenJdbc, Clock.fixed(Instant.parse("2026-08-08T07:00:00Z"), ZoneOffset.UTC));
        AchievementEventFact.Draft draft = new AchievementEventFact.Draft(
                UUID.randomUUID(),
                "event:unknown-failure",
                new AchievementAwardState.Key(1, 7, new AchievementKey("participation.1.gridwords")),
                AchievementDefinitionVersion.ACHIEVEMENTS_V1,
                AchievementEventFact.Type.UNLOCKED,
                LocalDate.of(2026, 8, 8),
                AchievementEvidence.Kind.GAME_RESULT,
                "game-result:1",
                AchievementEventFact.ProcessingOrigin.LIVE_SUBMISSION,
                Instant.parse("2026-08-08T07:00:00Z"));

        assertThatThrownBy(() -> store.append(draft)).isSameAs(failure);
    }
}
