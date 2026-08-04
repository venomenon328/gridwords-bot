package de.venomenon.gridwordsbot.domain.record;

/** Relative Near-Miss-Regel: mindestens ein Tag, sonst aufgerundete zehn Prozent ohne Obergrenze. */
public final class RecordNearMissPolicy {
    private RecordNearMissPolicy() {
    }

    public static int window(int referenceRecordLength) {
        if (referenceRecordLength <= 0) {
            throw new IllegalArgumentException("referenceRecordLength must be positive");
        }
        return Math.max(1, Math.ceilDiv(referenceRecordLength, 10));
    }

    public static boolean isNearMiss(int referenceRecordLength, int candidateLength) {
        if (candidateLength <= 0) {
            throw new IllegalArgumentException("candidateLength must be positive");
        }
        int difference = referenceRecordLength - candidateLength;
        return difference >= 1 && difference <= window(referenceRecordLength);
    }
}
