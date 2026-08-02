package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.venomenon.gridwordsbot.application.reporting.PeriodicReportDeliveryService;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportUseCase;
import de.venomenon.gridwordsbot.application.reporting.ReportDayAndStreakProjector;
import de.venomenon.gridwordsbot.application.reporting.ReportGameStatisticsProjector;
import de.venomenon.gridwordsbot.application.reporting.ReportParticipantProjector;
import de.venomenon.gridwordsbot.application.reporting.WeeklyReportReconciliationService;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationPlanner;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import de.venomenon.gridwordsbot.port.out.ReportGameResultQuery;
import de.venomenon.gridwordsbot.port.out.ReportParticipantQuery;
import de.venomenon.gridwordsbot.port.out.ReportStreakHistoryQuery;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class WeeklyReportConfigurationTest {

    @Test
    void databaseProfileWiresTheCompleteWeeklyPathWithAnInjectedGateway() {
        try (AnnotationConfigApplicationContext context = context(true)) {
            assertThat(context.getBeansOfType(ReportParticipantProjector.class)).hasSize(1);
            assertThat(context.getBeansOfType(ReportGameStatisticsProjector.class)).hasSize(1);
            assertThat(context.getBeansOfType(ReportDayAndStreakProjector.class)).hasSize(1);
            assertThat(context.getBeansOfType(PeriodicReportUseCase.class)).hasSize(1);
            assertThat(context.getBeansOfType(PeriodicReportReconciliationPlanner.class)).hasSize(1);
            assertThat(context.getBeansOfType(PeriodicReportDeliveryService.class)).hasSize(1);
            assertThat(context.getBeansOfType(WeeklyReportReconciliationService.class)).hasSize(1);
            assertThat(context.getBeansOfType(WeeklyReportScheduler.class)).hasSize(1);
        }
    }

    @Test
    void databaseProfileWithoutAGatewayHasNoDeliveryDependentWeeklyServiceOrScheduler() {
        try (AnnotationConfigApplicationContext context = context(false)) {
            assertThat(context.getBeansOfType(PeriodicReportDeliveryService.class)).isEmpty();
            assertThat(context.getBeansOfType(WeeklyReportReconciliationService.class)).isEmpty();
            assertThat(context.getBeansOfType(WeeklyReportScheduler.class)).isEmpty();
        }
    }

    private static AnnotationConfigApplicationContext context(boolean gateway) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("database");
        context.registerBean(Clock.class, Clock::systemUTC);
        context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
        context.registerBean(GridwordsBotProperties.class, WeeklyReportConfigurationTest::properties);
        context.registerBean(ReportParticipantQuery.class, () -> mock(ReportParticipantQuery.class));
        context.registerBean(ReportGameResultQuery.class, () -> mock(ReportGameResultQuery.class));
        context.registerBean(ReportStreakHistoryQuery.class, () -> mock(ReportStreakHistoryQuery.class));
        if (gateway) {
            context.registerBean(PeriodicReportMessageGateway.class, () -> mock(PeriodicReportMessageGateway.class));
        }
        context.register(PeriodicReportDeliveryConfiguration.class, WeeklyReportScheduler.class);
        context.refresh();
        return context;
    }

    private static GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(false, "", 11L, 12L, List.of()),
                new GridwordsBotProperties.Schedule(
                        LocalTime.of(18, 0),
                        LocalTime.of(23, 0),
                        LocalTime.of(8, 0),
                        LocalTime.of(8, 15),
                        ZoneId.of("Europe/Berlin")),
                new GridwordsBotProperties.Storage(48));
    }
}
