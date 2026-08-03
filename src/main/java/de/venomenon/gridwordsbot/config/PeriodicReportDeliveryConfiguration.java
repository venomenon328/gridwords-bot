package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.reporting.JdaPeriodicReportMessageGateway;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresPeriodicReportDeliveryStore;
import de.venomenon.gridwordsbot.application.reporting.MonthlyReportReconciliationService;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportDeliveryService;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportRenderer;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportUseCase;
import de.venomenon.gridwordsbot.application.reporting.ReportDayAndStreakProjector;
import de.venomenon.gridwordsbot.application.reporting.ReportGameStatisticsProjector;
import de.venomenon.gridwordsbot.application.reporting.ReportParticipantProjector;
import de.venomenon.gridwordsbot.application.reporting.WeeklyReportReconciliationService;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationPlanner;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import de.venomenon.gridwordsbot.port.out.ReportGameResultQuery;
import de.venomenon.gridwordsbot.port.out.ReportParticipantQuery;
import de.venomenon.gridwordsbot.port.out.ReportStreakHistoryQuery;
import java.time.Clock;
import net.dv8tion.jda.api.JDA;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires transport-neutral periodic report delivery only when its persistence profile is active. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
class PeriodicReportDeliveryConfiguration {

    @Bean
    PeriodicReportDeliveryStore periodicReportDeliveryStore(JdbcTemplate jdbc, Clock clock) {
        return new PostgresPeriodicReportDeliveryStore(jdbc, clock);
    }

    @Bean
    PeriodicReportRenderer periodicReportRenderer() {
        return new PeriodicReportRenderer();
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(PeriodicReportMessageGateway.class)
    PeriodicReportMessageGateway periodicReportMessageGateway(JDA jda) {
        return new JdaPeriodicReportMessageGateway(jda);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    PeriodicReportDeliveryService periodicReportDeliveryService(
            PeriodicReportDeliveryStore store,
            PeriodicReportMessageGateway messages,
            PeriodicReportRenderer renderer,
            Clock clock) {
        return new PeriodicReportDeliveryService(store, messages, renderer, clock);
    }

    @Bean
    ReportParticipantProjector reportParticipantProjector(ReportParticipantQuery participants) {
        return new ReportParticipantProjector(participants);
    }

    @Bean
    ReportGameStatisticsProjector reportGameStatisticsProjector(ReportGameResultQuery results) {
        return new ReportGameStatisticsProjector(results);
    }

    @Bean
    ReportDayAndStreakProjector reportDayAndStreakProjector(ReportStreakHistoryQuery history) {
        return new ReportDayAndStreakProjector(history);
    }

    @Bean
    PeriodicReportUseCase periodicReportUseCase(
            ReportParticipantProjector participants,
            ReportGameStatisticsProjector statistics,
            ReportDayAndStreakProjector daysAndStreaks) {
        return new PeriodicReportUseCase(participants, statistics, daysAndStreaks);
    }

    @Bean
    PeriodicReportReconciliationPlanner periodicReportReconciliationPlanner() {
        return new PeriodicReportReconciliationPlanner();
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    WeeklyReportReconciliationService weeklyReportReconciliationService(
            PeriodicReportDeliveryStore store,
            PeriodicReportReconciliationPlanner planner,
            PeriodicReportUseCase reports,
            PeriodicReportDeliveryService delivery,
            Clock clock,
            GridwordsBotProperties properties) {
        return new WeeklyReportReconciliationService(
                store,
                planner,
                reports,
                delivery,
                clock,
                properties.schedule().weeklyReport(),
                properties.schedule().timeZone());
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    MonthlyReportReconciliationService monthlyReportReconciliationService(
            PeriodicReportDeliveryStore store,
            PeriodicReportReconciliationPlanner planner,
            PeriodicReportUseCase reports,
            PeriodicReportDeliveryService delivery,
            Clock clock,
            GridwordsBotProperties properties) {
        return new MonthlyReportReconciliationService(
                store,
                planner,
                reports,
                delivery,
                clock,
                properties.schedule().monthlyReport(),
                properties.schedule().timeZone());
    }
}
