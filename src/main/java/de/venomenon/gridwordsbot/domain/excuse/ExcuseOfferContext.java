package de.venomenon.gridwordsbot.domain.excuse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

/**
 * Restart-safe facts from the first offer decision. The submission time and day comparison never
 * change; the fingerprint represents the current result-dependent context used for revalidation.
 */
public record ExcuseOfferContext(
        Instant originalReceivedAt,
        DailyComparisonSnapshot dailyComparison,
        String contextFingerprint) {

    private static final int SHA_256_HEX_LENGTH = 64;

    public ExcuseOfferContext {
        Objects.requireNonNull(originalReceivedAt, "originalReceivedAt");
        Objects.requireNonNull(dailyComparison, "dailyComparison");
        Objects.requireNonNull(contextFingerprint, "contextFingerprint");
        if (!contextFingerprint.matches("[0-9a-f]{" + SHA_256_HEX_LENGTH + "}")) {
            throw new IllegalArgumentException("contextFingerprint must be a lowercase SHA-256 hex value");
        }
    }

    public static ExcuseOfferContext initial(Instant originalReceivedAt, ExcuseEligibility eligibility) {
        Objects.requireNonNull(eligibility, "eligibility");
        return new ExcuseOfferContext(
                originalReceivedAt,
                eligibility.comparisonSnapshot(),
                fingerprint(originalReceivedAt, eligibility));
    }

    public ExcuseOfferContext withCurrentFingerprint(ExcuseEligibility eligibility) {
        Objects.requireNonNull(eligibility, "eligibility");
        if (eligibility.comparisonSnapshot().gameType() != dailyComparison.gameType()) {
            throw new IllegalArgumentException("eligibility must use the frozen comparison game type");
        }
        return new ExcuseOfferContext(originalReceivedAt, dailyComparison, fingerprint(originalReceivedAt, eligibility));
    }

    private static String fingerprint(Instant originalReceivedAt, ExcuseEligibility eligibility) {
        DailyComparisonSnapshot comparison = eligibility.comparisonSnapshot();
        String input = new StringBuilder("excuse-context-v1\n")
                .append(originalReceivedAt).append('\n')
                .append(eligibility.context().gameType()).append('\n')
                .append(comparison.gameType()).append('\n')
                .append(comparison.comparedResultCount()).append('\n')
                .append(comparison.allComparedResultsSolved()).append('\n')
                .append(comparison.highestSolvedAttempts().isPresent()
                        ? comparison.highestSolvedAttempts().getAsInt() : "none").append('\n')
                .append(comparison.longestDuration().toSeconds()).append('\n')
                .append(eligibility.eligible()).append('\n')
                .append(eligibility.reasons().stream().map(ExcuseReason::name).sorted().toList()).append('\n')
                .append(eligibility.context().conditions().stream().map(ExcuseCondition::key).sorted().toList()).append('\n')
                .append(eligibility.context().placeholders().entrySet().stream()
                        .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                        .map(entry -> entry.getKey().name() + "=" + entry.getValue()).toList())
                .toString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(SHA_256_HEX_LENGTH);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
