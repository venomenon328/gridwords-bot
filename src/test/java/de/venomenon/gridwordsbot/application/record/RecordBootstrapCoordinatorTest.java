package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordBootstrapCoordinatorTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private final RecordHistorySnapshot history = new RecordHistorySnapshot(List.of(), List.of(
            new GameParticipationPeriod(7L, GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), null)));

    @Test void keepsYesterdayOpenBeforeTheBusinessCutoff() {
        var window = RecordBootstrapCoordinator.analysisWindow(history,
                Clock.fixed(Instant.parse("2026-08-05T03:59:59Z"), BERLIN));
        assertThat(window.asOfDate()).isEqualTo(LocalDate.of(2026, 8, 4));
        assertThat(window.asOfDateClosed()).isFalse();
    }

    @Test void closesYesterdayAtTheBusinessCutoffWithoutClosingToday() {
        var window = RecordBootstrapCoordinator.analysisWindow(history,
                Clock.fixed(Instant.parse("2026-08-05T04:00:00Z"), BERLIN));
        assertThat(window.asOfDate()).isEqualTo(LocalDate.of(2026, 8, 4));
        assertThat(window.asOfDateClosed()).isTrue();
    }
}
