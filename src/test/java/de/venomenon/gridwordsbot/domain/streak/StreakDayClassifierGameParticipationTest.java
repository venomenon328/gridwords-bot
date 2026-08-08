package de.venomenon.gridwordsbot.domain.streak;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreakDayClassifierGameParticipationTest {
    private static final long PLAYER = 7L;
    private static final LocalDate DAY = LocalDate.of(2026, 8, 8);

    @Test
    void personalParticipationCountsAnyValidResultRegardlessOfSuccess() {
        StreakDayClassifier classifier = new StreakDayClassifier(
                List.of(new StreakGameResult(PLAYER, DAY, GameType.GRIDWORDS, false)),
                List.of(new GameParticipationPeriod(PLAYER, GameType.GRIDWORDS, DAY.minusDays(1), null)));

        assertThat(classifier.personalParticipation(PLAYER, DAY, GameType.GRIDWORDS, true))
                .isEqualTo(StreakDayAssessment.met());
    }

    @Test
    void personalParticipationUsesDayCloseAndParticipationBoundaries() {
        StreakDayClassifier active = new StreakDayClassifier(
                List.of(),
                List.of(new GameParticipationPeriod(PLAYER, GameType.GRIDWORDS, DAY.minusDays(1), null)));

        assertThat(active.personalParticipation(PLAYER, DAY, GameType.GRIDWORDS, false))
                .isEqualTo(StreakDayAssessment.pending());
        assertThat(active.personalParticipation(PLAYER, DAY, GameType.GRIDWORDS, true))
                .isEqualTo(StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.DAY_CLOSE));
        assertThat(active.personalParticipation(PLAYER, DAY, GameType.QUADWORDS, true))
                .isEqualTo(StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.PARTICIPATION));
    }
}
