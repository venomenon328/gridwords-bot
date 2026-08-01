package de.venomenon.gridwordsbot.domain.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PeriodicReportDeliveryContractsTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void deliveryKeyUsesEveryBusinessIdentityPart() {
        PeriodicReportDeliveryKey key = key();

        assertThat(key)
                .isNotEqualTo(new PeriodicReportDeliveryKey(2, 20, ReportType.WEEKLY, key.periodStart()))
                .isNotEqualTo(new PeriodicReportDeliveryKey(1, 21, ReportType.WEEKLY, key.periodStart()))
                .isNotEqualTo(new PeriodicReportDeliveryKey(1, 20, ReportType.MONTHLY, key.periodStart()))
                .isNotEqualTo(new PeriodicReportDeliveryKey(1, 20, ReportType.WEEKLY, key.periodStart().plusDays(1)));
    }

    @Test
    void rejectsInvalidIdsPeriodsTimesTokensAndPageValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportDeliveryKey(0, 20, ReportType.WEEKLY, LocalDate.now()));
        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportDeliveryMetadata(
                period(), dueAt(), dueAt().instant()));
        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportDeliveryClaim(
                new UUID(0, 0), Instant.parse("2026-08-03T08:01:00Z")));
        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportDeliveryClaimRequest(
                Instant.parse("2026-08-03T08:00:00Z"), Instant.parse("2026-08-03T08:00:00Z")));
        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportDeliveryContent("not-a-fingerprint", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportDeliveryPageProgress(-1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportDeliveryFailure(
                PeriodicReportDeliveryFailureCategory.RETRYABLE, "line one\nline two"));
    }

    @Test
    void snapshotPreservesContiguousPageOrderAndDefensivelyCopiesIt() {
        List<PeriodicReportDeliveryPageProgress> progress = new ArrayList<>(List.of(
                new PeriodicReportDeliveryPageProgress(0, 101),
                new PeriodicReportDeliveryPageProgress(1, 102)));

        PeriodicReportDeliverySnapshot snapshot = snapshot(
                PeriodicReportDeliveryState.CLAIMED, Optional.of(claim()), Optional.empty(), Optional.empty(), progress, Optional.empty());
        progress.clear();

        assertThat(snapshot.pageProgress()).containsExactly(
                new PeriodicReportDeliveryPageProgress(0, 101), new PeriodicReportDeliveryPageProgress(1, 102));
        assertThatIllegalArgumentException().isThrownBy(() -> snapshot(
                PeriodicReportDeliveryState.CLAIMED,
                Optional.of(claim()),
                Optional.empty(),
                Optional.empty(),
                List.of(new PeriodicReportDeliveryPageProgress(1, 101)),
                Optional.empty()));
    }

    @Test
    void terminalStatesAreNeverClaimableAndOnlyClaimedStateRetainsAClaim() {
        assertThat(PeriodicReportDeliveryState.OPEN.isClaimable()).isTrue();
        assertThat(PeriodicReportDeliveryState.RETRYABLE.isClaimable()).isTrue();
        assertThat(PeriodicReportDeliveryState.CLAIMED.isClaimable()).isFalse();
        assertThat(PeriodicReportDeliveryState.values())
                .filteredOn(PeriodicReportDeliveryState::isTerminal)
                .allMatch(state -> !state.isClaimable());
        assertThatIllegalArgumentException().isThrownBy(() -> snapshot(
                PeriodicReportDeliveryState.OPEN, Optional.of(claim()), Optional.empty(), Optional.empty(), List.of(), Optional.empty()));
    }

    @Test
    void deliveryDomainAndPortsDoNotDependOnInfrastructureTypes() {
        var classes = new ClassFileImporter().importPackages(
                "de.venomenon.gridwordsbot.domain.reporting", "de.venomenon.gridwordsbot.port.out");

        assertThat(classes.stream()
                .filter(javaClass -> javaClass.getPackageName().contains("reporting")
                        || javaClass.getName().contains("PeriodicReport"))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .map(dependency -> dependency.getTargetClass().getPackageName()))
                .noneMatch(packageName -> packageName.startsWith("net.dv8tion")
                        || packageName.startsWith("org.springframework")
                        || packageName.startsWith("jakarta.persistence")
                        || packageName.startsWith("org.springframework.jdbc"));
    }

    private static PeriodicReportDeliverySnapshot snapshot(
            PeriodicReportDeliveryState state,
            Optional<PeriodicReportDeliveryClaim> claim,
            Optional<Instant> nextRetryAt,
            Optional<PeriodicReportDeliveryFailure> failure,
            List<PeriodicReportDeliveryPageProgress> progress,
            Optional<Instant> completedAt) {
        Instant createdAt = Instant.parse("2026-08-03T08:00:00Z");
        return new PeriodicReportDeliverySnapshot(
                new PeriodicReportDeliveryRegistration(key(), metadata(), Optional.of(new PeriodicReportDeliveryContent(FINGERPRINT, 2))),
                state, claim, 1, nextRetryAt, failure, progress, completedAt, createdAt, createdAt);
    }

    private static PeriodicReportDeliveryKey key() {
        return new PeriodicReportDeliveryKey(1, 20, ReportType.WEEKLY, period().startDate());
    }

    private static PeriodicReportDeliveryMetadata metadata() {
        return new PeriodicReportDeliveryMetadata(period(), dueAt(), Instant.parse("2026-08-06T06:00:00Z"));
    }

    private static ReportPeriod period() {
        return new ReportPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
    }

    private static ReportDueAt dueAt() {
        return new ReportDueAt(LocalDate.of(2026, 8, 3), LocalTime.of(8, 0), BERLIN);
    }

    private static PeriodicReportDeliveryClaim claim() {
        return new PeriodicReportDeliveryClaim(UUID.randomUUID(), Instant.parse("2026-08-03T08:05:00Z"));
    }
}
