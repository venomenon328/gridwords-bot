# Abnahmeprotokoll Inkrement 12

Dieses Protokoll bündelt die technische End-to-End-Abnahme für Issue #58 und Paket #83. Es ist kein Release- oder RC-Protokoll.

## Status

**Technischer Stand:** Maven-Gates bestanden; Image-Runtime und Compose-Validierung bestanden

**Reale Discord-Abnahme:** offen – separater Testserver erforderlich

**Container-Betriebscheck:** offen – dieser Windows-Host besitzt keine funktionierende Bash-Laufzeit
**Release / RC / Produktion:** gesperrt

Der PR bleibt Draft, bis die Container-/Betriebschecks, die unabhängige Review und sämtliche folgenden Discord-Punkte nachweisbar bestanden sind. Die Maven-Gates liefen auf diesem Branch erfolgreich. Es werden keine Tokens, Discord-IDs, Rohshares oder personenbezogenen Testdaten in diesem Dokument erfasst.

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

- `RecordLiveEvaluationMigrationIT` prüft den Upgradepfad vom Schema bis 019; `PostgresSchemaIT` prüft den Neuaufbau mit aktuellem Liquibase-Master.
- `PostgresRecordBootstrapCoordinatorIT` deckt Bootstrap-Neustart, Teilfortschritt und die Fortsetzung einer historischen laufenden Serie ab.
- `PostgresRecordAnnouncementDeliveryRecoveryIT.restartReconcilesAPageCreatedBeforeTheDeliveryAcknowledgementWithoutDuplicatingIt` hinterlässt nach einem absichtlich verlorenen technischen ACK einen abgelaufenen PostgreSQL-Claim ohne gespeicherte Message-ID. Eine neue Koordinatorinstanz reclaimt ihn, entdeckt die bereits veröffentlichte Seite und synchronisiert sie ohne zweiten Create. Zusammen mit `PostgresRecordClaimLeaseFencingIT` deckt das konkurrierende Worker, Lease-Ablauf, Restart und Multipage-Reconciliation mit echtem PostgreSQL ab.
- `RecordLiveEvaluationCoordinatorTest` und `RecordAnnouncementDeliveryCoordinatorTest` beweisen, dass unbekannte technische Fehler weder als fachlicher Konflikt noch als fachlicher permanenter Fehler maskiert werden.
- `RecordsQueryServiceTest` und `DiscordRecordsCommandListenerTest` prüfen `category:results|series`, die Kombination mit `game`, die unveränderte Sicht spielunabhängiger Serien, Admin-Fremdansicht, Ephemeralität und leere Allowed Mentions.

## Reale Discord-Abnahme – offen

Die folgenden Schritte müssen auf einer separaten Discord-Testanwendung, einem separaten Guild/Channel und einer isolierten PostgreSQL-Datenbank durchgeführt werden. Vor dem Test werden die Gates dieses Dokuments erfolgreich ausgeführt; die lokale RC-Erzeugung ist erst nach Merge dieses PRs ein separater Schritt.

- [ ] Eine aggregierte Live-Ergebnisrekordmeldung mit mehreren Fakten prüfen.
- [ ] Persönliche und serverweite Serienüberschreitung prüfen.
- [ ] Mehrteilige Serienabschlussmeldung nach explizitem `X` prüfen.
- [ ] Abschluss beim fachlichen 06:00-Close mit kontrollierter Berlin-Clock prüfen.
- [ ] Gleichstand, Near Miss und negative Durststrecke prüfen.
- [ ] Korrektur mit Edit sowie Korrektur mit vollständigem Delete einer Rekordmeldung prüfen.
- [ ] `/records`, Admin-Fremdansicht und `game`-Sichten prüfen.
- [ ] `/records category:results`, `/records category:series` sowie eine Kombination wie `game:gridwords category:results` prüfen.
- [ ] Für jede Command-Seite Ephemeralität, Pagination und das Ausbleiben von Mentions prüfen.
- [ ] Restart nach Send-vor-ACK und Recovery ohne doppelte öffentliche Projektion prüfen.
- [ ] Öffentliche Meldungen deaktivieren, neue Ereignisse erzeugen und nach Reaktivierung das Ausbleiben eines Backlogs prüfen.

Für jedes Kästchen werden Datum, geprüfter Commit, Bild-/Lognachweis ohne Secrets und das beobachtete Ergebnis im PR festgehalten. Erst dann darf dieser Status auf bestanden gesetzt werden.

## Technische und betriebliche Gates

```powershell
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
docker build -t gridwords-bot:ci .
docker compose --project-name gridwords-compose-config-test --env-file scripts/test/production-runtime.test.env --env-file scripts/test/deployment.env -f compose.production.yaml config
bash scripts/test/production-compose-contextual-excuses.sh
bash scripts/test/production-operations.sh
```

Am 7. August 2026 wurden auf diesem Branch tatsächlich erreicht:

- `clean verify`: **BUILD SUCCESS**, 766 Tests, 0 Fehler.
- `-Pdatabase-integration clean verify`: **BUILD SUCCESS**, 766 Standard- plus 241 Failsafe-PostgreSQL-Integrationstests, jeweils 0 Fehler.
- `docker build -t gridwords-bot:ci .`: erfolgreich; Runtime-Prüfung bestätigt UID 10001, JAR vorhanden sowie keine `.env`, keine Quellen und kein Maven-Cache im Runtimeimage.
- `docker compose … config`: erfolgreich; internes PostgreSQL-Netz ohne Hostport, Read-only-Bot und Testkonfiguration validiert.

Die beiden Bash-Skripte konnten auf diesem Host nicht gestartet werden, weil die aufgerufene WSL-Bash keine `/bin/bash`-Distribution besitzt. Der darin enthaltene vollständige isolierte Backup-/Restore-/Resume-/Rollbackcheck bleibt deshalb offen und muss in GitHub Actions oder auf einem Host mit Bash ausgeführt werden. Das ist kein Erfolgsausweis.

## RC-Sperre und unveröffentlichte Release Notes

Der separate RC darf erst nach Merge des vollständig abgenommenen PRs aus dessen unverändertem `main`-Commit entstehen. Dieses Paket erzeugt keinen RC-Tag, kein Image in GHCR und keinen Produktivrollout. Die inhaltlichen, noch unveröffentlichten Release Notes stehen in [12-records-release-notes.md](12-records-release-notes.md).
