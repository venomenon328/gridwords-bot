package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class RecordLiveEvaluationSchedulerTest {
    @Test
    void usesTheTypedLiveEvaluationPollDelayForNonOverlappingFixedDelayScheduling() throws Exception {
        Method poll = RecordLiveEvaluationScheduler.class.getDeclaredMethod("poll");
        Scheduled scheduled = poll.getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString()).isEqualTo("#{@recordLiveEvaluationPollDelayMillis}");
        GridwordsBotProperties.Records records = new GridwordsBotProperties.Records(
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(1), true,
                Duration.ofMillis(7), Duration.ofSeconds(10), Duration.ofSeconds(3),
                Duration.ofSeconds(1), Duration.ofSeconds(5));
        GridwordsBotProperties properties = new GridwordsBotProperties(null, null, null, null, null, records);

        assertThat(new RecordPersistenceConfiguration().recordLiveEvaluationPollDelayMillis(properties)).isEqualTo(7L);
    }
}
