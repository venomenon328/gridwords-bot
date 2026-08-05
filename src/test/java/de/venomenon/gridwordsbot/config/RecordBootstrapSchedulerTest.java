package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class RecordBootstrapSchedulerTest {
    @Test
    void usesTheTypedPollDelayForScheduling() throws Exception {
        Method poll = RecordBootstrapScheduler.class.getDeclaredMethod("poll");
        Scheduled scheduled = poll.getAnnotation(Scheduled.class);
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("#{@recordBootstrapPollDelayMillis}");
        GridwordsBotProperties.Records values = new GridwordsBotProperties.Records(
                Duration.ofMillis(713), Duration.ofSeconds(2), Duration.ofSeconds(1));
        assertThat(values.bootstrapPollDelay().toMillis()).isEqualTo(713L);
        GridwordsBotProperties properties = new GridwordsBotProperties(null, null, null, null, null, values);
        assertThat(new RecordPersistenceConfiguration().recordBootstrapPollDelayMillis(properties)).isEqualTo(713L);
    }

    @Test
    void minimumAcceptedPollDelayRemainsOneSchedulerMillisecond() {
        GridwordsBotProperties.Records values = new GridwordsBotProperties.Records(
                Duration.ofMillis(1), Duration.ofSeconds(2), Duration.ofSeconds(1));
        GridwordsBotProperties properties = new GridwordsBotProperties(null, null, null, null, null, values);

        assertThat(new RecordPersistenceConfiguration().recordBootstrapPollDelayMillis(properties)).isEqualTo(1L);
    }
}
