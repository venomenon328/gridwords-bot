package de.venomenon.gridwordsbot.port.out;

import java.util.function.Supplier;

/** Infrastructure-owned transaction boundary for coupled record writes. */
public interface RecordTransactionRunner {
    <T> T inTransaction(Supplier<T> work);
}
