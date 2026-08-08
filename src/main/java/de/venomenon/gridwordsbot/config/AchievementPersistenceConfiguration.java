package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementAnnouncementStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementAwardStateStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementBootstrapStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementEventStore;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementBootstrapStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("db")
@ConditionalOnBean(JdbcTemplate.class)
public class AchievementPersistenceConfiguration {
    @Bean
    AchievementAwardStateStore achievementAwardStateStore(JdbcTemplate jdbc, Clock clock) {
        return new PostgresAchievementAwardStateStore(jdbc, clock);
    }

    @Bean
    AchievementEventStore achievementEventStore(JdbcTemplate jdbc, Clock clock) {
        return new PostgresAchievementEventStore(jdbc, clock);
    }

    @Bean
    AchievementBootstrapStore achievementBootstrapStore(JdbcTemplate jdbc, Clock clock) {
        return new PostgresAchievementBootstrapStore(jdbc, clock);
    }

    @Bean
    AchievementAnnouncementStore achievementAnnouncementStore(JdbcTemplate jdbc, Clock clock) {
        return new PostgresAchievementAnnouncementStore(jdbc, clock);
    }
}
