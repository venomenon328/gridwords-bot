package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementAnnouncementStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementAwardStateStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementBootstrapStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementEventStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementHistoryQuery;
import de.venomenon.gridwordsbot.application.achievement.AchievementReconciliationService;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvaluator;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementBootstrapStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import de.venomenon.gridwordsbot.port.out.AchievementHistoryQuery;
import de.venomenon.gridwordsbot.port.out.AchievementTransactionRunner;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Profile({"db", "database"})
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

    @Bean
    AchievementHistoryQuery achievementHistoryQuery(JdbcTemplate jdbc) {
        return new PostgresAchievementHistoryQuery(jdbc);
    }

    @Bean
    AchievementDefinitionCatalog achievementDefinitionCatalog() {
        return AchievementDefinitionCatalog.achievementsV1();
    }

    @Bean
    @ConditionalOnBean(TransactionTemplate.class)
    AchievementTransactionRunner achievementTransactionRunner(TransactionTemplate transactions) {
        return new AchievementTransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return transactions.execute(status -> work.get());
            }
        };
    }

    @Bean
    @ConditionalOnBean(AchievementTransactionRunner.class)
    AchievementReconciliationService achievementReconciliationService(
            AchievementHistoryQuery history,
            AchievementDefinitionCatalog catalog,
            AchievementAwardStateStore awards,
            AchievementEventStore events,
            AchievementAnnouncementStore announcements,
            AchievementTransactionRunner transactions,
            Clock clock) {
        return new AchievementReconciliationService(
                history,
                new AchievementEvaluator(catalog),
                catalog,
                awards,
                events,
                announcements,
                transactions,
                clock,
                AchievementEvaluator.DEFAULT_TIME_ZONE);
    }
}
