package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.achievement.AchievementHistorySnapshot;

/**
 * Canonical participant-scoped history required by achievement reconciliation.
 *
 * <p>The returned snapshot deliberately contains only persisted domain facts; it is not an
 * achievement projection and must never be reconstructed from Discord output.</p>
 */
public interface AchievementHistoryQuery {
    AchievementHistorySnapshot load(long guildId, long participantId);
}
