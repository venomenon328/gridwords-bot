package de.venomenon.gridwordsbot.adapter.discord.status;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.status.DailyStatusView;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DailyStatusComponentRendererTest {
    @Test
    void rejectsMoreThanFiftyParticipantsBeforeDiscordIo() {
        DailyStatus status = new DailyStatus(LocalDate.of(2026, 8, 3), IntStream.rangeClosed(1, 51)
                .mapToObj(id -> new DailyStatus.PlayerLine(id, "Player " + id, Optional.empty(), Optional.empty(),
                        new StreakSummary(0, 0, 0, 0, 0, 0, 0))).toList(), 0, 0);

        assertThatThrownBy(() -> new DailyStatusComponentRenderer().render(DailyStatusView.versionOne(status)))
                .isInstanceOf(DiscordDeliveryException.class)
                .matches(error -> ((DiscordDeliveryException) error).permanent());
    }
}