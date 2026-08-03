package de.venomenon.gridwordsbot.application.status;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.status.DailyStatusView;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
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

    @Test
    void componentVersionOptionsAndPageLayoutAffectTheFingerprint() {
        DailyStatus status = componentStatus(26);
        DailyStatusView original = DailyStatusView.versionOne(status);
        String originalFingerprint = DailyStatusRefreshService.fingerprint(original);

        DailyStatusView newVersion = new DailyStatusView(status, 2, original.resultMenuPages());
        List<DailyStatusView.DailyResultMenuPage> renamedPages = new ArrayList<>(original.resultMenuPages());
        var first = renamedPages.getFirst();
        List<DailyStatusView.PlayerOption> renamedOptions = new ArrayList<>(first.options());
        renamedOptions.set(0, new DailyStatusView.PlayerOption(
                renamedOptions.getFirst().discordUserId(), "Renamed"));
        renamedPages.set(0, new DailyStatusView.DailyResultMenuPage(
                first.gameType(), first.pageIndex(), first.pageCount(), renamedOptions));
        DailyStatusView renamedOption = new DailyStatusView(status, 1, renamedPages);
        DailyStatusView differentPages = new DailyStatusView(
                status,
                1,
                List.of(
                        new DailyStatusView.DailyResultMenuPage(
                                GameType.GRIDWORDS, 0, 1, original.resultMenuPages().get(0).options()),
                        new DailyStatusView.DailyResultMenuPage(
                                GameType.QUADWORDS, 0, 1, original.resultMenuPages().get(2).options())));

        assertThat(DailyStatusRefreshService.fingerprint(newVersion)).isNotEqualTo(originalFingerprint);
        assertThat(DailyStatusRefreshService.fingerprint(renamedOption)).isNotEqualTo(originalFingerprint);
        assertThat(DailyStatusRefreshService.fingerprint(differentPages)).isNotEqualTo(originalFingerprint);
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

    private static DailyStatus componentStatus(int playerCount) {
        List<DailyStatus.PlayerLine> players = IntStream.rangeClosed(1, playerCount)
                .mapToObj(id -> new DailyStatus.PlayerLine(
                        id,
                        String.format("Player %02d", id),
                        Optional.empty(),
                        Optional.empty(),
                        new StreakSummary(0, 0, 0, 0, 0, 0, 0)))
                .toList();
        return new DailyStatus(DATE, players, 0, 0);
    }
}
