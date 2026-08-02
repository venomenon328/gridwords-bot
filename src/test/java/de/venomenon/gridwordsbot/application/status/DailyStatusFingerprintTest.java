package de.venomenon.gridwordsbot.application.status;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailyStatusFingerprintTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 30);

    @Test
    void fingerprintIsStableAndIncludesBothSharedGameSolvedStreaks() {
        DailyStatus base = status(3, 2);
        DailyStatus same = status(3, 2);
        DailyStatus changedGrid = status(4, 2);
        DailyStatus changedQuad = status(3, 4);

        assertThat(DailyStatusRefreshService.fingerprint(same))
                .isEqualTo(DailyStatusRefreshService.fingerprint(base));
        assertThat(DailyStatusRefreshService.fingerprint(changedGrid))
                .isNotEqualTo(DailyStatusRefreshService.fingerprint(base));
        assertThat(DailyStatusRefreshService.fingerprint(changedQuad))
                .isNotEqualTo(DailyStatusRefreshService.fingerprint(base));
        assertThat(DailyStatusRefreshService.fingerprint(changedGrid))
                .isNotEqualTo(DailyStatusRefreshService.fingerprint(changedQuad));
    }

    private static DailyStatus status(int sharedGridWordsSolved, int sharedQuadWordsSolved) {
        DailyStatus.PlayerLine player = new DailyStatus.PlayerLine(
                42L,
                "Player",
                Optional.empty(),
                Optional.empty(),
                new StreakSummary(1, 0, 1, 0, 0,
                        sharedGridWordsSolved, sharedQuadWordsSolved, 1, 0));
        return new DailyStatus(
                DATE,
                List.of(player),
                sharedGridWordsSolved,
                sharedQuadWordsSolved,
                1,
                0);
    }
}
