package de.venomenon.gridwordsbot.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecordScopeAndSourceTest {

    @Test
    void exposesTheThreeExplicitScopeTypes() {
        assertThat(new RecordScope.Personal(42).type()).isEqualTo(RecordScopeType.PERSONAL);
        assertThat(new RecordScope.ServerIndividual().type()).isEqualTo(RecordScopeType.SERVER_INDIVIDUAL);
        assertThat(new RecordScope.Shared().type()).isEqualTo(RecordScopeType.SHARED);
        assertThatIllegalArgumentException().isThrownBy(() -> new RecordScope.Personal(0));
    }

    @Test
    void validatesTransportNeutralGameResultReferences() {
        LocalDate gameDate = LocalDate.of(2026, 8, 4);
        var source = new RecordSourceReference.GameResult(10, 2, 42, GameType.GRIDWORDS, gameDate);

        assertThat(source.sourceType()).isEqualTo(RecordSourceType.GAME_RESULT);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RecordSourceReference.GameResult(0, 2, 42, GameType.GRIDWORDS, gameDate));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RecordSourceReference.GameResult(10, -1, 42, GameType.GRIDWORDS, gameDate));
    }

    @Test
    void allowsOnlyMetricsWithSharedSemanticsToUseSharedStreakSources() {
        LocalDate start = LocalDate.of(2026, 7, 1);

        assertThatCode(() -> new RecordSourceReference.StreakRun(
                StreakRecordMetric.COMPLETE,
                new RecordSourceReference.StreakRunOwner.Shared(),
                start)).doesNotThrowAnyException();
        assertThatIllegalArgumentException().isThrownBy(() -> new RecordSourceReference.StreakRun(
                StreakRecordMetric.ACTIVITY,
                new RecordSourceReference.StreakRunOwner.Shared(),
                start));
        assertThatIllegalArgumentException().isThrownBy(() -> new RecordSourceReference.StreakRun(
                StreakRecordMetric.GRIDWORDS_DROUGHT,
                new RecordSourceReference.StreakRunOwner.Shared(),
                start));
    }

    @Test
    void marksOnlyLiveOriginsAsPubliclyAnnouncementEligible() {
        assertThat(RecordProcessingOrigin.LIVE_SUBMISSION.publicAnnouncementEligible()).isTrue();
        assertThat(RecordProcessingOrigin.NORMAL_CORRECTION.publicAnnouncementEligible()).isTrue();
        assertThat(RecordProcessingOrigin.DAY_CLOSE.publicAnnouncementEligible()).isTrue();
        assertThat(RecordProcessingOrigin.BOOTSTRAP.publicAnnouncementEligible()).isFalse();
        assertThat(RecordProcessingOrigin.REPLAY.publicAnnouncementEligible()).isFalse();
        assertThat(RecordProcessingOrigin.IMPORT.publicAnnouncementEligible()).isFalse();
        assertThat(RecordProcessingOrigin.BACKFILL.publicAnnouncementEligible()).isFalse();
        assertThat(RecordProcessingOrigin.ADMINISTRATIVE_REPAIR.publicAnnouncementEligible()).isFalse();
    }
}
