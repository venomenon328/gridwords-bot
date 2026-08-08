package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementBootstrapStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class AchievementPersistenceConfigurationTest {
    @Test
    void exposesAchievementPersistenceOnlyWithDbProfileAndJdbcTemplate() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("db");
            context.registerBean(JdbcTemplate.class, JdbcTemplate::new);
            context.registerBean(Clock.class, Clock::systemUTC);
            context.register(AchievementPersistenceConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(AchievementAwardStateStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(AchievementEventStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(AchievementBootstrapStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(AchievementAnnouncementStore.class)).hasSize(1);
        }
    }
}
