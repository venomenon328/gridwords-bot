package de.venomenon.gridwordsbot.port.out;

import java.util.function.Supplier;

/** Infrastructure-owned transaction boundary for coupled achievement projection writes. */
public interface AchievementTransactionRunner {
    <T> T inTransaction(Supplier<T> work);
}
