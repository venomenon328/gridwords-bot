package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.application.reporting.MonthlyReportReconciliationService;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportDeliveryService;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportUseCase;
import de.venomenon.gridwordsbot.application.reporting.ReportDayAndStreakProjector;
import de.venomenon.gridwordsbot.application.reporting.ReportGameStatisticsProjector;
import de.venomenon.gridwordsbot.application.reporting.ReportParticipantProjector;
import de.venomenon.gridwordsbot.application.reporting.WeeklyReportReconciliationService;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryExpiration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryScope;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationPlanner;
import de.venomenon.gridwordsbot.domain.reporting.ReportDueAt;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import de.venomenon.gridwordsbot.port.out.ReportGameResultQuery;
import de.venomenon.gridwordsbot.port.out.ReportParticipantQuery;
import de.venomenon.gridwordsbot.port.out.ReportStreakHistoryQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

class WeeklyReportConfigurationTest {

    @Test
    void databaseProfileWiresIndependentWeeklyAndMonthlyPathsWithAnInjectedGateway() {
        try (AnnotationConfigApplicationContext context = context(true)) {
            assertThat(context.getBeansOfType(ReportParticipantProjector.class)).hasSize(1);
            assertThat(context.getBeansOfType(ReportGameStatisticsProjector.class)).hasSize(1);
            assertThat(context.getBeansOfType(ReportDayAndStreakProjector.class)).hasSize(1);
            assertThat(context.getBeansOfType(PeriodicReportUseCase.class)).hasSize(1);
            assertThat(context.getBeansOfType(PeriodicReportReconciliationPlanner.class)).hasSize(1);
            assertThat(context.getBeansOfType(PeriodicReportDeliveryService.class)).hasSize(1);
            assertThat(context.getBeansOfType(WeeklyReportReconciliationService.class)).hasSize(1);
            assertThat(context.getBeansOfType(MonthlyReportReconciliationService.class)).hasSize(1);
            assertThat(context.getBeansOfType(WeeklyReportScheduler.class)).hasSize(1);
            assertThat(context.getBeansOfType(MonthlyReportScheduler.class)).hasSize(1);
        }
    }

    @Test
    void databaseProfileWithDiscordDisabledKeepsTheReportingCoreWithoutDeliveryServices() {
        try (AnnotationConfigApplicationContext context = context(false)) {
            assertThat(context.getBeansOfType(ReportParticipantProjector.class)).hasSize(1);
            assertThat(context.getBeansOfType(ReportGameStatisticsProjector.class)).hasSize(1);
            assertThat(context.getBeansOfType(ReportDayAndStreakProjector.class)).hasSize(1);
            assertThat(context.getBeansOfType(PeriodicReportUseCase.class)).hasSize(1);
            assertThat(context.getBeansOfType(PeriodicReportReconciliationPlanner.class)).hasSize(1);
            assertThat(context.getBeansOfType(PeriodicReportDeliveryService.class)).isEmpty();
            assertThat(context.getBeansOfType(WeeklyReportReconciliationService.class)).isEmpty();
            assertThat(context.getBeansOfType(MonthlyReportReconciliationService.class)).isEmpty();
            assertThat(context.getBeansOfType(WeeklyReportScheduler.class)).isEmpty();
            assertThat(context.getBeansOfType(MonthlyReportScheduler.class)).isEmpty();
        }
    }

    @Test
    void monthlyServiceBeanUsesTheConfiguredMonthlyTimeAndZone() {
        Instant now = Instant.parse("2026-09-08T06:15:00Z");
        PeriodicReportDeliveryStore store = mock(PeriodicReportDeliveryStore.class);
        PeriodicReportDeliveryScope scope =
                new PeriodicReportDeliveryScope(11L, 12L, ReportType.MONTHLY);
        when(store.findLatestPeriodStart(scope)).thenReturn(Optional.empty());
        MonthlyReportReconciliationService service = new PeriodicReportDeliveryConfiguration()
                .monthlyReportReconciliationService(
                        store,
                        new PeriodicReportReconciliationPlanner(),
                        mock(PeriodicReportUseCase.class),
                        mock(PeriodicReportDeliveryService.class),
                        Clock.fixed(now, ZoneOffset.UTC),
                        properties(true));

        service.reconcile(11L, 12L);

        ArgumentCaptor<PeriodicReportDeliveryExpiration> expiration =
                ArgumentCaptor.forClass(PeriodicReportDeliveryExpiration.class);
        verify(store).expire(expiration.capture(), eq(now));
        assertThat(expiration.getValue().metadata().dueAt())
                .isEqualTo(new ReportDueAt(
                        LocalDate.of(2026, 9, 1),
                        LocalTime.of(8, 15),
                        ZoneId.of("Europe/Berlin")));
    }

    private static AnnotationConfigApplicationContext context(boolean discordEnabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("database");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "test-discord",
                Map.of("gridwords.discord.enabled", Boolean.toString(discordEnabled))));
        context.registerBean(Clock.class, Clock::systemUTC);
        context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
        context.registerBean(GridwordsBotProperties.class, () -> properties(discordEnabled));
        context.registerBean(ReportParticipantQuery.class,
                () -> mock(ReportParticipantQuery.class));
        context.registerBean(ReportGameResultQuery.class,
                () -> mock(ReportGameResultQuery.class));
        context.registerBean(ReportStreakHistoryQuery.class,
                () -> mock(ReportStreakHistoryQuery.class));
        if (discordEnabled) {
            context.registerBean(PeriodicReportMessageGateway.class,
                    () -> mock(PeriodicReportMessageGateway.class));
        }
        context.register(
                PeriodicReportDeliveryConfiguration.class,
                WeeklyReportScheduler.class,
                MonthlyReportScheduler.class);
        context.refresh();
        return context;
    }

    private static GridwordsBotProperties properties(boolean discordEnabled) {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(discordEnabled, "", 11L, 12L, List.of()),
                new GridwordsBotProperties.Schedule(
                        LocalTime.of(18, 0),
                        LocalTime.of(23, 0),
                        LocalTime.of(8, 0),
                        LocalTime.of(8, 15),
                        ZoneId.of("Europe/Berlin")),
                new GridwordsBotProperties.Storage(48));
    }
}
