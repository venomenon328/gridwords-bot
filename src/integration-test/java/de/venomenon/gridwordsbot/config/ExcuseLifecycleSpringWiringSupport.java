package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/** Shared fixture for proving the actual Spring-managed result-storage wiring. */
abstract class ExcuseLifecycleSpringWiringSupport {

    protected static final long PLAYER_ID = 78_001L;
    private static final long SOURCE_MESSAGE_ID = 78_002L;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PlayerStore players;

    @Autowired
    private SubmissionStore submissions;

    @Autowired
    private ExcuseStateStore excuses;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE TABLE player RESTART IDENTITY CASCADE");
        players.upsert(new PlayerStore.PlayerUpsert(PLAYER_ID, "Spring wiring", true, false));
    }

    ExcuseStatus storeQualifyingResult() {
        Instant receivedAt = Instant.now();
        LocalDate gameDate = LocalDate.now(BERLIN);
        submissions.register(new SubmissionStore.SubmissionRegistration(
                SOURCE_MESSAGE_ID,
                78_003L,
                78_004L,
                PLAYER_ID,
                "redacted-spring-wiring-fixture",
                List.of(),
                receivedAt));

        ParsedGameResult parsed = new ParsedGameResult(
                GameType.GRIDWORDS,
                gameDate,
                new ShareOutcome.Solved(6, 6),
                Duration.ofMinutes(5),
                OptionalInt.empty(),
                Optional.of(new NormalizedBoard(List.of(
                        "⬜⬜⬜⬜⬜",
                        "⬜⬜⬜⬜⬜",
                        "⬜⬜⬜⬜⬜",
                        "⬜⬜⬜⬜⬜",
                        "⬜⬜⬜⬜⬜",
                        "🟩🟩🟩🟩🟩"))));

        SubmissionStore.StoredSubmission stored = submissions.storeResult(new SubmissionStore.ResultStorage(
                SOURCE_MESSAGE_ID,
                new GameResultStore.GameResultUpsert(
                        PLAYER_ID,
                        parsed,
                        "redacted-spring-wiring-fixture",
                        "gridwords-v1")));
        long resultId = stored.gameResultId().orElseThrow();
        return excuses.find(resultId).orElseThrow().status();
    }
}
