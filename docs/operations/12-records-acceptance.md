# Abnahmeprotokoll Inkrement 12

Dieses Protokoll bündelt die technische End-to-End-Abnahme für Issue #58 und Paket #83. Es ist kein Release- oder RC-Protokoll.

## Status

**Technischer Stand:** Review-Nacharbeit umgesetzt; die vollständigen Maven- und Container-Gates müssen auf dem aktuellen Head grün sein

**Reale Discord-Abnahme:** offen – separater Testserver erforderlich

**Container-Betriebscheck:** im Draft-PR automatisiert; `Container image` führt die nicht publizierenden Image-, Compose-, Backup-, Restore-, Resume- und Rollbackchecks auch vor `ready_for_review` aus

**Release / RC / Produktion:** gesperrt

Der PR bleibt Draft, bis die technischen Gates, die unabhängige Review und sämtliche folgenden Discord-Punkte nachweisbar bestanden sind. Es werden keine Tokens, Discord-IDs, Rohshares oder personenbezogenen Testdaten in diesem Dokument erfasst.

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

- `RecordLiveEvaluationMigrationIT` prüft mit echtem PostgreSQL den produktionsrelevanten Upgradepfad vom Schema **017** über 018–022 zum aktuellen Liquibase-Master. Spieler-, Ergebnis-, spielbezogene Teilnahme-, Ausreden- und Ausredenkontextdaten bleiben erhalten; zugleich werden der aktuelle Record-Schemaumfang, der Live-Trigger und der 022-Deliveryindex nachgewiesen. `PostgresSchemaIT` prüft zusätzlich den Neuaufbau mit aktuellem Master.
- `PostgresRecordShareToAnnouncementE2EIT` startet mit fünf historischen Vergleichsergebnissen, führt den Kandidaten über `ProcessSharedResultService` und die realen PostgreSQL-Adapter ein, verarbeitet die durch den Submission-Trigger registrierte Live-Arbeit über den echten Record-Processor und synchronisiert die daraus erzeugte aggregierte Announcement-Projektion über den echten Delivery-Koordinator. Nur das äußerste `RecordAnnouncementMessageGateway` ist ein kontrollierter Discord-Fake.
- `PostgresRecordBootstrapCoordinatorIT` deckt Bootstrap-Neustart, Teilfortschritt und die Fortsetzung einer historischen laufenden Serie ab.
- `PostgresRecordAnnouncementDeliveryRecoveryIT.restartReconcilesAPageCreatedBeforeTheDeliveryAcknowledgementWithoutDuplicatingIt` hinterlässt nach einem absichtlich verlorenen technischen ACK einen abgelaufenen PostgreSQL-Claim ohne gespeicherte Message-ID. Eine neue Koordinatorinstanz reclaimt ihn, entdeckt die bereits veröffentlichte Seite und synchronisiert sie ohne zweiten Create. Zusammen mit `PostgresRecordClaimLeaseFencingIT` deckt das konkurrierende Worker, Lease-Ablauf, Restart und Multipage-Reconciliation mit echtem PostgreSQL ab.
- `RecordLiveEvaluationCoordinatorTest` und `RecordAnnouncementDeliveryCoordinatorTest` beweisen, dass unbekannte technische Fehler weder als fachlicher Konflikt noch als fachlicher permanenter Fehler maskiert werden.
- `RecordsQueryServiceTest` und `DiscordRecordsCommandListenerTest` prüfen `category:all|results|series`, die Kombination mit `game`, das fachlich verbindliche Ausblenden spielunabhängiger Serien bei gesetztem Game-Filter, Admin-Fremdansicht, Ephemeralität und leere Allowed Mentions.

## Reale Discord-Abnahme – offen

Die folgenden Schritte müssen auf einer separaten Discord-Testanwendung, einem separaten Guild/Channel und einer isolierten PostgreSQL-Datenbank durchgeführt werden. Vor dem Test werden die Gates dieses Dokuments erfolgreich ausgeführt; die lokale RC-Erzeugung ist erst nach Merge dieses PRs ein separater Schritt.

- [ ] Eine aggregierte Live-Ergebnisrekordmeldung mit mehreren Fakten prüfen.
- [ ] Persönliche und serverweite Serienüberschreitung prüfen.
- [ ] Mehrteilige Serienabschlussmeldung nach explizitem `X` prüfen.
- [ ] Abschluss beim fachlichen 06:00-Close mit kontrollierter Berlin-Clock prüfen.
- [ ] Gleichstand, Near Miss und negative Durststrecke prüfen.
- [ ] Korrektur mit Edit sowie Korrektur mit vollständigem Delete einer Rekordmeldung prüfen.
- [ ] `/records`, Admin-Fremdansicht und `game`-Sichten prüfen; bei gesetztem `game`-Filter dürfen Aktivität, Komplett, Perfekt und „ohne perfekten Tag“ nicht erscheinen.
- [ ] `/records category:all`, `/records category:results`, `/records category:series` sowie eine Kombination wie `game:gridwords category:results` prüfen.
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

Für den aktuellen Head sind beide Maven-Befehle verbindlich. Der Workflow `Container image` führt dieselben Maven-Gates sowie Image-Runtime, Shellcheck und den vollständigen isolierten Compose-/Backup-/Restore-/Resume-/Rollbackpfad auf Ubuntu aus. Diese nicht publizierenden Jobs laufen ausdrücklich auch bei Draft-PRs. Der Publish-Job bleibt ausschließlich `workflow_dispatch` auf `main` vorbehalten und ist kein Bestandteil dieser Abnahme.

## RC-Sperre und unveröffentlichte Release Notes

Der separate RC darf erst nach Merge des vollständig abgenommenen PRs aus dessen unverändertem `main`-Commit entstehen. Dieses Paket erzeugt keinen RC-Tag, kein Image in GHCR und keinen Produktivrollout. Die inhaltlichen, noch unveröffentlichten Release Notes stehen in [12-records-release-notes.md](12-records-release-notes.md).
