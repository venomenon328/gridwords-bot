package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.venomenon.gridwordsbot.application.achievement.AchievementBootstrapCoordinator;
import de.venomenon.gridwordsbot.application.achievement.AchievementReconciliationService;
import de.venomenon.gridwordsbot.application.achievement.AchievementResultLifecycle;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementBootstrapStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import de.venomenon.gridwordsbot.port.out.AchievementHistoryQuery;
import de.venomenon.gridwordsbot.port.out.AchievementTransactionRunner;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AchievementPersistenceConfigurationTest {
    @Test
    void exposesAchievementRuntimeWhenInfrastructureArrivesAsRegularBeanDefinitions() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("database");

            // Register Achievement wiring first on purpose. Production infrastructure is discovered as normal
            // bean definitions as well and must not depend on @ConditionalOnBean evaluation order.
            context.register(AchievementPersistenceConfiguration.class, InfrastructureConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(AchievementAwardStateStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(AchievementEventStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(AchievementBootstrapStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(AchievementAnnouncementStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(AchievementHistoryQuery.class)).hasSize(1);
            assertThat(context.getBean(AchievementDefinitionCatalog.class).definitions()).hasSize(60);
            assertThat(context.getBeansOfType(AchievementTransactionRunner.class)).hasSize(1);
            assertThat(context.getBean(AchievementReconciliationService.class)).isNotNull();
            assertThat(context.getBean(AchievementResultLifecycle.class)).isNotNull();
            assertThat(context.getBean(AchievementBootstrapCoordinator.class)).isNotNull();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InfrastructureConfiguration {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        TransactionTemplate transactionTemplate(DataSource dataSource) {
            return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        }

        @Bean
        SubmissionStore submissionStore() {
            return mock(SubmissionStore.class);
        }

        @Bean
        PlayerStore playerStore() {
            return mock(PlayerStore.class);
        }
    }
}
