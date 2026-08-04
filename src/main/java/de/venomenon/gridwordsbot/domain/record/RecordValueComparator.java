package de.venomenon.gridwordsbot.domain.record;

/** Expliziter fachlicher Vergleich: Kandidat gegen aktuellen Rekord. */
public interface RecordValueComparator<V extends RecordValue> {
    Class<V> valueType();

    RecordComparison compare(V candidate, V current);
}
