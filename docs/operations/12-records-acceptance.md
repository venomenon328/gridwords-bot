# Abnahmeprotokoll Inkrement 12

Dieses Protokoll bündelt die technische End-to-End-Abnahme für Issue #58 und Paket #83. Es ist kein Release- oder RC-Protokoll.

## Status

**Technischer Stand:** bestanden. Der manuell abgenommene Code-Head ist `9921b43cad5e2558ec36923d62ffdbbfcb660321`; GitHub Actions CI #1368 und der vollständige Workflow `Container image` #390 sind auf diesem Head grün.

**Reale Discord-Abnahme:** für Merge freigegeben. Auf dem separaten Testserver wurden die risikoreichen Live-, Korrektur-, Delivery-, Recovery-, `/records`- und Serienpfade repräsentativ geprüft. Die verbleibenden seltenen Serienvarianten werden nach ausdrücklicher Projektentscheidung nicht als weiterer Merge-Blocker behandelt, sondern zusätzlich im laufenden Betrieb beobachtet; ihre Fachlogik besitzt automatisierte Abdeckung.

**Release / RC / Produktion:** Inkrement 12 ist mergefähig. RC und produktiver Rollout bleiben separate nachgelagerte Schritte mit eigenem Backup-, Smoke- und Rollbacknachweis.

Es werden keine Tokens, Discord-IDs, Rohshares oder personenbezogenen Testdaten in diesem Dokument erfasst.

## Akzeptanzmatrix

Die Referenzen benennen bewusst Tests auf der passenden Ebene. Alle `Postgres…IT` laufen ausschließlich im Maven-Profil `database-integration` gegen Testcontainers-PostgreSQL; sie sind kein H2-Ersatz.

| Fälle | Nachweis |
|---|---|
| 1–3 | `ResultRecordEvaluatorTest` (Initialisierung, persönliche und serverweite Mindesthistorie), `PostgresRecordLiveEvaluationProcessorIT` |
| 4–7 | `RecordComparatorsTest`, `ResultRecordEvaluatorTest`, `ResultRecordObservationTest` |
| 8–11 | `ResultRecordEvaluatorTest`, `RecordAnnouncementRendererTest`, `PostgresRecordLiveEvaluationProcessorIT` |
| 12–19 | `StreakRecordEvaluatorTest`, `StreakRunReconcilerTest`, `RecordLiveEvaluationProcessorTest` |
| 20–23 | `StreakRunAnalyzerTest`, `RecordDayCloseServiceTest`, `PostgresRecordDayCloseProcessorIT`, `RecordBootstrapCoordinatorTest` |
| 24–30 | `StreakRunAnalyzerTest`, `StreakRecordEvaluatorTest`, `PostgresRecordLiveEvaluationProcessorIT` |
| 31–32 | `PostgresRecordLiveEvaluationProcessorIT` (Korrektur mit Teilreduktion, Edit und Delete) |
| 33 | `PostgresRecordPersistenceStoreIT`, `PostgresRecordBootstrapCoordinatorIT`, `PostgresRecordLiveEvaluationProcessorIT` – getrennte Datenbankverbindungen, Transaktionen und Latches |
| 34–36 | `PostgresRecordClaimLeaseFencingIT`, `PostgresRecordAnnouncementDeliveryRecoveryIT`, `RecordAnnouncementDeliveryCoordinatorTest` |
| 37–38 | `RecordBootstrapProjectionTest`, `StreakRunAnalyzerTest`, `RecordsQueryServiceTest`, `RecordAnnouncementRendererTest` |
| 39 | `RecordsQueryServiceTest`, `DiscordRecordsCommandListenerTest`, `RecordsQueryReadOnlyInvariantTest` |
| 40 | `RecordBootstrapCoordinatorTest`, `StreakRunAnalyzerTest`, `RecordDayCloseServiceTest` mit fester `Clock` und `Europe/Berlin` |

Zusätzliche End-to-End-Nachweise:

- `RecordLiveEvaluationMigrationIT` prüft mit echtem PostgreSQL den produktionsrelevanten Upgradepfad vom Schema **017** über 018–023 zum aktuellen Liquibase-Master. Spieler-, Ergebnis-, spielbezogene Teilnahme-, Ausreden- und Ausredenkontextdaten bleiben erhalten; zugleich werden der aktuelle Record-Schemaumfang, der Live-Trigger und der Deliveryindex nachgewiesen. `RecordAnnouncementStabilityMigrationIT` weist zusätzlich nach, dass ausschließlich bereits veröffentlichte Create-Projektionen aus 022 mit persistierter Message-ID genau einen ID-basierten Cleanup-Edit zum Entfernen des alten sichtbaren Recovery-Links erhalten. `PostgresSchemaIT` prüft den Neuaufbau mit aktuellem Master.
- `PostgresRecordShareToAnnouncementE2EIT` führt einen gültigen Share über `ProcessSharedResultService`, die realen PostgreSQL-Adapter, den Record-Processor und den Delivery-Koordinator bis zur aggregierten Announcement-Projektion. Eine normale Korrektur läuft bis zum ID-basierten Edit und `SYNCHRONIZED`.
- `PostgresRecordBootstrapCoordinatorIT` deckt Bootstrap-Neustart, Teilfortschritt und die Fortsetzung einer historischen laufenden Serie ab.
- `PostgresRecordRunningStreakCrossingIT` deckt die bei der realen Abnahme gefundene Bootstrap-Grenze ab: ein laufender Gleichstand darf beim ersten späteren strikten Übertreffen genau einmal melden; ein bereits beim Bootstrap kanonischer laufender Rekord bleibt bei weiterer Verlängerung still.
- `PostgresRecordAnnouncementDeliveryRecoveryIT` und `PostgresRecordClaimLeaseFencingIT` decken konkurrierende Worker, Lease-Ablauf, Restart, ACK-loss und Reconciliation ohne Duplicate Create mit echtem PostgreSQL ab.
- `PostgresRecordAnnouncementDeliveryRecoveryIT.createStabilityEditPartialReductionAndDeleteUsePersistedMessageIds` durchläuft Create, einmalige Stabilitätsprüfung, Edit, Teilreduktion und Delete. `SYNCHRONIZED` bleibt danach ruhig und normale Discord-Aktionen verwenden persistierte Message-IDs.
- `CanonicalGridWordsPublicationServiceTest` und `PostgresPersistenceAdapterIT` beweisen nach der Performance-Nacharbeit, dass normale kanonische Creates und ID-basierte Edits keine vollständige Discord-History-Discovery mehr ausführen. Discovery bleibt ausschließlich dem mehrdeutigen Recovery-Fall mit älterem ungelöstem Delivery-Versuch vorbehalten.
- `RecordLiveEvaluationCoordinatorTest` und `RecordAnnouncementDeliveryCoordinatorTest` beweisen, dass unbekannte technische Fehler weder als fachlicher Konflikt noch als fachlicher permanenter Fehler maskiert werden.
- `RecordsQueryServiceTest` und `DiscordRecordsCommandListenerTest` prüfen `category`, Kombinationen mit `game`, die verbindliche Game-Filter-Semantik, Admin-Fremdansicht, Ephemeralität und leere Allowed Mentions.

## Reale Discord-Abnahme

Am 7./8. August 2026 wurde der Pfad auf separatem Testserver und isolierter PostgreSQL-Datenbank iterativ geprüft. Maßgeblicher zuletzt abgenommener Code-Head ist `9921b43cad5e2558ec36923d62ffdbbfcb660321`.

Manuell nachgewiesen wurden insbesondere:

- [x] aggregierte Live-Ergebnisrekordmeldung mit mehreren Fakten,
- [x] persönliche und serverweite Ergebnisrekorde mit korrekter Halterdarstellung,
- [x] kein sichtbarer technischer Recovery-Key oder Recovery-Link in neuen Rekordmeldungen,
- [x] stabile Delivery `CREATE -> DELIVERED -> SYNCHRONIZED` ohne dauerhaft hochlaufenden `attempt_count`,
- [x] Restart ohne Duplicate Create sowie persistierte Message-ID,
- [x] Korrektur mit Edit, Teilreduktion und vollständigem Delete einer Rekordmeldung,
- [x] global deaktivierte neue Rekordmeldungen mit `SUPPRESSED` und ohne nachträglichen Backlog nach Reaktivierung,
- [x] `/records` für Ergebnisse/Serien und GridWords sowie Admin-Fremdansicht; Ausgabe ephemeral,
- [x] persönliche und serverweite positive Serienüberschreitung nach historischem 7-Tage-Gleichstand,
- [x] Korrektur einer laufenden Lösungsserie durch `X`: Aktivitäts-Crossings bleiben gültig, Lösungs-Crossings werden invalidiert/reconciliiert,
- [x] kanonischer Ergebnis-Happy-Path nach Performance-Nacharbeit: Erst-Create und ID-basierte Korrektur erfolgen ohne channelweiten History-Scan und wieder mit unauffälliger Latenz.

Bewusst nicht als zusätzlicher manueller Merge-Blocker wiederholt wurden der gemeinsame Serienfall, negative Durststrecke und ein realer nächtlicher 06:00-Day-Close. Die zugrunde liegende Fachlogik, Berlin-Cutoff-/Clock-Semantik, Day-Close-Persistenz und Serienklassifikation sind automatisiert abgedeckt. Die Projektentscheidung lautet, diese seltenen Varianten zusätzlich im laufenden Betrieb zu beobachten, statt die Produktivsetzung für weitere synthetische Discord-Szenarien aufzuschieben.

Diese Restbeobachtung ändert nicht die Rollback-Regel: Bei Record-/Announcement-Problemen können öffentliche Rekordmeldungen deaktiviert werden, während Record-State und `/records` weiterlaufen; unbekannte technische Fehler werden diagnostizierbar persistiert.

## Technische und betriebliche Gates

Verbindliche Gates:

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

Für `9921b43cad5e2558ec36923d62ffdbbfcb660321` sind die lokalen Gates nach der letzten Performance-Nacharbeit grün bestätigt. GitHub Actions CI #1368 und `Container image` #390 sind ebenfalls vollständig grün; der Container-Workflow umfasst Image-Runtime, Shellcheck sowie den isolierten Compose-, Backup-, Restore-, Resume- und Rollbackpfad.

## Merge- und Releaseentscheidung

Paket 12.10 und damit Inkrement 12 sind für den Merge freigegeben. Der Merge selbst erzeugt noch keinen RC, kein Registry-Tag und keinen Produktivrollout.

Der nachfolgende Releasepfad lautet bewusst separat:

1. unveränderten `main`-Stand nach Merge als RC-/Releasekandidaten verwenden,
2. Backup und Rollbackziel vorhalten,
3. Testserver-/RC-Smoke durchführen,
4. Produktion zunächst mit öffentlichen Record-Announcements deaktiviert starten,
5. Liquibase, Bootstrap und `/records` gegen die echte Historie prüfen,
6. erst anschließend öffentliche Record-Announcements aktivieren und die ersten Live-Ereignisse beobachten.

Die inhaltlichen, noch unveröffentlichten Release Notes stehen in [12-records-release-notes.md](12-records-release-notes.md).
