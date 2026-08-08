package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementBootstrapStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import de.venomenon.gridwordsbot.port.out.AchievementHistoryQuery;
import de.venomenon.gridwordsbot.port.out.AchievementTransactionRunner;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.application.achievement.AchievementReconciliationService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class AchievementPersistenceConfigurationTest {
    @Test
    void exposesAchievementPersistenceWithTheProductionDatabaseProfileAndJdbcTemplate() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("database");
            context.getBeanFactory().registerSingleton("jdbcTemplate", new JdbcTemplate());
            context.getBeanFactory().registerSingleton("clock", Clock.systemUTC());
            context.getBeanFactory().registerSingleton(
                    "transactionTemplate",
                    new TransactionTemplate(new DataSourceTransactionManager(new DriverManagerDataSource())));
            context.register(AchievementPersistenceConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(AchievementAwardStateStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(AchievementEventStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(AchievementBootstrapStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(AchievementAnnouncementStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(AchievementHistoryQuery.class)).hasSize(1);
            assertThat(context.getBean(AchievementDefinitionCatalog.class).definitions()).hasSize(60);
            assertThat(context.getBeansOfType(AchievementTransactionRunner.class)).hasSize(1);
            assertThat(context.getBean(AchievementReconciliationService.class)).isNotNull();
        }
    }
}
