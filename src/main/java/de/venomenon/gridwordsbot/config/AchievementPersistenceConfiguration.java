package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementAnnouncementStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementAwardStateStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementBootstrapStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementEventStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresAchievementHistoryQuery;
import de.venomenon.gridwordsbot.application.achievement.AchievementReconciliationService;
import de.venomenon.gridwordsbot.application.achievement.AchievementAnnouncementDeliveryCoordinator;
import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.application.achievement.AchievementBootstrapCoordinator;
import de.venomenon.gridwordsbot.application.achievement.AchievementResultLifecycle;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvaluator;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementBootstrapStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import de.venomenon.gridwordsbot.port.out.AchievementHistoryQuery;
import de.venomenon.gridwordsbot.port.out.AchievementTransactionRunner;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementMessageGateway;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
    AchievementEmojiResolver achievementEmojiResolver() {
        return AchievementEmojiResolver.unicodeOnly();
    }

    @Bean
    @ConditionalOnBean(TransactionTemplate.class)
    AchievementTransactionRunner achievementTransactionRunner(TransactionTemplate transactions, JdbcTemplate jdbc) {
        return new AchievementTransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return transactions.execute(status -> work.get());
            }

            @Override
            public <T> T inParticipantTransaction(long participantId, java.util.function.Supplier<T> work) {
                if (participantId <= 0) {
                    throw new IllegalArgumentException("participantId must be positive");
                }
                return transactions.execute(status -> {
                    Long lockedParticipant = jdbc.queryForObject(
                            "SELECT discord_user_id FROM player WHERE discord_user_id=? FOR UPDATE",
                            Long.class,
                            participantId);
                    if (lockedParticipant == null || lockedParticipant.longValue() != participantId) {
                        throw new IllegalStateException("achievement participant lock could not be acquired");
                    }
                    return work.get();
                });
            }

            @Override
            public <T> T inBootstrapFenceTransaction(
                    de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork.BootstrapKey key,
                    java.util.function.Supplier<T> work) {
                return transactions.execute(status -> {
                    Integer locked = jdbc.queryForObject("""
                            SELECT 1 FROM achievement_bootstrap_state
                             WHERE guild_id=? AND definition_version=?
                             FOR UPDATE
                            """, Integer.class, key.guildId(), key.definitionVersion().value());
                    if (locked == null || locked != 1) {
                        throw new IllegalStateException("achievement bootstrap fence could not be acquired");
                    }
                    return work.get();
                });
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

    @Bean
    @ConditionalOnBean({AchievementReconciliationService.class, SubmissionStore.class})
    AchievementResultLifecycle achievementResultLifecycle(
            AchievementBootstrapStore bootstraps,
            AchievementTransactionRunner transactions,
            AchievementReconciliationService reconciliation,
            AchievementDefinitionCatalog catalog,
            AchievementAwardStateStore awards,
            AchievementEventStore events,
            AchievementAnnouncementStore announcements,
            SubmissionStore submissions,
            Clock clock) {
        return new AchievementResultLifecycle(
                bootstraps, transactions, reconciliation, catalog, awards, events, announcements, submissions, clock);
    }

    @Bean
    @ConditionalOnBean({AchievementResultLifecycle.class, PlayerStore.class})
    AchievementBootstrapCoordinator achievementBootstrapCoordinator(
            AchievementBootstrapStore bootstraps,
            AchievementTransactionRunner transactions,
            PlayerStore players,
            AchievementResultLifecycle lifecycle,
            AchievementDefinitionCatalog catalog,
            Clock clock) {
        return new AchievementBootstrapCoordinator(
                bootstraps, transactions, players, lifecycle, catalog, clock,
                Duration.ofMinutes(2), Duration.ofMinutes(1));
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService achievementAnnouncementHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "achievement-announcement-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    @ConditionalOnBean(AchievementAnnouncementMessageGateway.class)
    AchievementAnnouncementDeliveryCoordinator achievementAnnouncementDeliveryCoordinator(
            AchievementAnnouncementStore announcements,
            AchievementEventStore events,
            AchievementAwardStateStore awards,
            PlayerStore players,
            AchievementAnnouncementMessageGateway messages,
            AchievementDefinitionCatalog catalog,
            AchievementEmojiResolver emojis,
            Clock clock,
            ScheduledExecutorService achievementAnnouncementHeartbeatExecutor,
            GridwordsBotProperties properties) {
        GridwordsBotProperties.Records delivery = properties.records();
        return new AchievementAnnouncementDeliveryCoordinator(
                announcements, events, awards, players, messages, catalog, emojis, clock,
                delivery.announcementLeaseDuration(), delivery.announcementHeartbeatInterval(),
                delivery.announcementInitialRetryBackoff(), delivery.announcementMaxRetryBackoff(),
                achievementAnnouncementHeartbeatExecutor);
    }

    @Bean(name = "achievementAnnouncementPollDelayMillis")
    @ConditionalOnBean(AchievementAnnouncementDeliveryCoordinator.class)
    long achievementAnnouncementPollDelayMillis(GridwordsBotProperties properties) {
        return properties.records().announcementPollDelay().toMillis();
    }

    @Bean
    @ConditionalOnBean(AchievementAnnouncementDeliveryCoordinator.class)
    org.springframework.boot.ApplicationRunner achievementAnnouncementDeliveryStartupRunner(
            AchievementAnnouncementDeliveryCoordinator coordinator) {
        return arguments -> coordinator.runNext();
    }
}
