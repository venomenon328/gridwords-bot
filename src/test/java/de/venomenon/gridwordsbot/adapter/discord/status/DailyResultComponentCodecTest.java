package de.venomenon.gridwordsbot.adapter.discord.status;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DailyResultComponentCodecTest {
    private final DailyResultComponentCodec codec = new DailyResultComponentCodec();
    @Test void roundTripsTheVersionOneContract() {
        assertThat(codec.decode("daily-result:v1:2026-08-03:g:1")).contains(new DailyResultComponentCodec.Component(LocalDate.of(2026,8,3), GameType.GRIDWORDS, 1));
        assertThat(codec.target("user:42")).contains(42L);
    }
    @Test void rejectsUnknownVersionAndManipulatedValues() {
        assertThat(codec.decode("daily-result:v2:2026-08-03:g:0")).isEmpty();
        assertThat(codec.decode("daily-result:v1:not-a-date:q:-1")).isEmpty();
        assertThat(codec.target("user:0")).isEmpty();
    }
}