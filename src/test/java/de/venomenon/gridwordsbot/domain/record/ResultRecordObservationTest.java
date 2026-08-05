package de.venomenon.gridwordsbot.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ResultRecordObservationTest {
    private static final LocalDate GAME_DATE = LocalDate.of(2026, 8, 1);
    private static final Instant ACCEPTED_AT = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void exposesTypedValuesAndStableSourceReference() {
        ResultRecordObservation observation = solved(
                41, 3, 7, GameType.GRIDWORDS, 2, Duration.ofSeconds(75));

        assertThat(observation.attemptsUsed()).isEqualTo(2);
        assertThat(observation.valueFor(ResultRecordMetric.FEWEST_ATTEMPTS))
                .isEqualTo(new AttemptsDurationRecordValue(2, Duration.ofSeconds(75)));
        assertThat(observation.valueFor(ResultRecordMetric.FASTEST_SOLUTION))
                .isEqualTo(new DurationRecordValue(Duration.ofSeconds(75)));
        assertThat(observation.valueFor(ResultRecordMetric.SLOWEST_SUCCESSFUL_SOLUTION))
                .isEqualTo(new DurationRecordValue(Duration.ofSeconds(75)));
        assertThat(observation.sourceReference()).isEqualTo(new RecordSourceReference.GameResult(
                41, 3, 7, GameType.GRIDWORDS, GAME_DATE));
    }

    @Test
    void rejectsUnsolvedAndGameInconsistentOutcomes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ResultRecordObservation(
                1,
                0,
                7,
                GameType.GRIDWORDS,
                GAME_DATE,
                ACCEPTED_AT,
                new ShareOutcome.Unsolved(6),
                Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new ResultRecordObservation(
                1,
                0,
                7,
                GameType.GRIDWORDS,
                GAME_DATE,
                ACCEPTED_AT,
                new ShareOutcome.Solved(2, 9),
                Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new ResultRecordObservation(
                1,
                0,
                7,
                GameType.QUADWORDS,
                GAME_DATE,
                ACCEPTED_AT,
                new ShareOutcome.Solved(2, 6),
                Duration.ZERO));
    }

    @Test
    void rejectsInvalidIdentityAndNegativeDuration() {
        assertThatIllegalArgumentException().isThrownBy(() -> solved(
                0, 0, 7, GameType.GRIDWORDS, 2, Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> solved(
                1, -1, 7, GameType.GRIDWORDS, 2, Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> solved(
                1, 0, 0, GameType.GRIDWORDS, 2, Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> solved(
                1, 0, 7, GameType.GRIDWORDS, 2, Duration.ofSeconds(-1)));
    }

    private static ResultRecordObservation solved(
            long resultId,
            long resultVersion,
            long playerId,
            GameType game,
            int attempts,
            Duration duration) {
        int maximumAttempts = game == GameType.GRIDWORDS ? 6 : 9;
        return new ResultRecordObservation(
                resultId,
                resultVersion,
                playerId,
                game,
                GAME_DATE,
                ACCEPTED_AT,
                new ShareOutcome.Solved(attempts, maximumAttempts),
                duration);
    }
}
