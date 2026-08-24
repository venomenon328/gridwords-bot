package de.venomenon.gridwordsbot.application.achievement;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionVersion;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementBootstrapStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import de.venomenon.gridwordsbot.port.out.AchievementTransactionRunner;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Durable-result handoff and bootstrap gate for normal and recovery Achievement reconciliation. */
public final class AchievementResultLifecycle {
    private final AchievementBootstrapStore bootstraps;
    private final AchievementTransactionRunner transactions;
    private final AchievementReconciliationService reconciliation;
    private final AchievementHistoricalIntroductionProjector introductions;
    private final AchievementDefinitionCatalog catalog;
    private final SubmissionStore submissions;

    public AchievementResultLifecycle(
            AchievementBootstrapStore bootstraps,
            AchievementTransactionRunner transactions,
            AchievementReconciliationService reconciliation,
            AchievementDefinitionCatalog catalog,
            AchievementAwardStateStore awardStates,
            AchievementEventStore events,
            AchievementAnnouncementStore announcements,
            SubmissionStore submissions,
            Clock clock) {
        this.bootstraps = Objects.requireNonNull(bootstraps, "bootstraps");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.reconciliation = Objects.requireNonNull(reconciliation, "reconciliation");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.introductions = new AchievementHistoricalIntroductionProjector(
                catalog, awardStates, events, announcements, clock);
        this.submissions = Objects.requireNonNull(submissions, "submissions");
    }

    /** Handles a freshly stored normal result before any canonical Discord I/O. */
    public void reconcileNormal(SubmissionStore.ResultStorageOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        if (!outcome.requiresNormalAchievementHandoff()) {
            return;
        }
        SubmissionStore.StoredSubmission submission = outcome.submission();
        if (submission.state() == SubmissionStore.SubmissionState.SUPERSEDED || submission.gameResultId().isEmpty()) {
            return;
        }
        AchievementEventFact.ProcessingOrigin origin = outcome.kind() == SubmissionStore.ResultStorageKind.NEW_RESULT
                ? AchievementEventFact.ProcessingOrigin.LIVE_SUBMISSION
                : AchievementEventFact.ProcessingOrigin.NORMAL_CORRECTION;
        reconcileAtBootstrapFence(
                submission.guildId(),
                submission.channelId(),
                submission.authorPlayerId(),
                origin,
                Optional.of(new AchievementReconciliationService.LiveAnnouncementTarget(
                        submission.channelId(), "source-message:" + submission.sourceMessageId())));
    }

    /** Replays persisted post-result work without retroactively creating a normal live announcement. */
    public void recoverPendingResults() {
        for (SubmissionStore.StoredSubmission submission : submissions.findAchievementRecoveryCandidates()) {
            if (submission.state() != SubmissionStore.SubmissionState.SUPERSEDED && submission.gameResultId().isPresent()) {
                reconcileAtBootstrapFence(
                        submission.guildId(),
                        submission.channelId(),
                        submission.authorPlayerId(),
                        AchievementEventFact.ProcessingOrigin.REPLAY,
                        Optional.empty());
            }
        }
    }

    void reconcileBootstrapParticipant(long guildId, long channelId, long participantId) {
        reconciliation.reconcile(new AchievementReconciliationService.ReconciliationRequest(
                guildId, participantId, AchievementEventFact.ProcessingOrigin.BOOTSTRAP, Optional.empty()));
        refreshHistoricalIntroductionIfRequired(guildId, channelId, participantId);
    }

    private void reconcileAtBootstrapFence(
            long guildId,
            long channelId,
            long participantId,
            AchievementEventFact.ProcessingOrigin origin,
            Optional<AchievementReconciliationService.LiveAnnouncementTarget> requestedLiveTarget) {
        AchievementWork.BootstrapKey key = new AchievementWork.BootstrapKey(guildId, catalog.version());
        bootstraps.register(key);
        transactions.inBootstrapFenceTransaction(key, () -> {
            AchievementWork.BootstrapSnapshot bootstrap = bootstraps.find(key)
                    .orElseThrow(() -> new IllegalStateException("registered achievement bootstrap disappeared"));
            if (bootstrap.state() == AchievementWork.State.SUCCEEDED) {
                reconciliation.reconcile(new AchievementReconciliationService.ReconciliationRequest(
                        guildId, participantId, origin, requestedLiveTarget));
            } else {
                reconciliation.reconcile(new AchievementReconciliationService.ReconciliationRequest(
                        guildId, participantId, origin, Optional.empty()));
                refreshHistoricalIntroductionIfRequired(guildId, channelId, participantId);
            }
            return null;
        });
    }

    private void refreshHistoricalIntroductionIfRequired(long guildId, long channelId, long participantId) {
        if (catalog.version().equals(AchievementDefinitionVersion.ACHIEVEMENTS_V1)) {
            introductions.refresh(guildId, channelId, participantId);
        }
    }
}
