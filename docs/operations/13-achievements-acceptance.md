# Abnahmeprotokoll Inkrement 13

Dieses Protokoll bündelt die technische End-to-End-Abnahme für Inkrement 13 / Issue #86 und Paket 13.8 / Issue #94 sowie den anschließenden realen Rollout-Nachweis.

## Status

**Technische Abnahme:** bestanden. Der finale Gesamt-PR #103 wurde nach vollständig grünen Standard-, PostgreSQL-, Migration-/Upgrade- und Container-/Betriebsgates nach `main` gemergt.

**Lokale reale Discord-Abnahme:** bestanden für Startup, Upgrade, Bootstrap, historische Introduction, `/achievements` und Restart-Idempotenz. Dabei wurden zwei reale Randfälle gefunden und vor Merge behoben: reihenfolgeabhängiges Spring-Wiring der Achievement-Persistenz sowie sichtbare Recovery-Metadaten im Discord-Embed.

**Produktivrelease:** **Version 1.4.0 erfolgreich ausgerollt.** Maßgeblicher Merge-Commit ist `213fe15dcc59e46856ea9be7066161fdc473353a`. Der ausgeführte Produktiv-Smoke/Canary ist bestanden; es wurde kein Abbruch- oder Rollbackkriterium ausgelöst.

Die tieferen Konkurrenz-, Retry-/Restart-, Korrektur-, Invalidierungs-, Reaktivierungs- und Send-vor-ACK-Szenarien bleiben zusätzlich durch die unten dokumentierten automatisierten PostgreSQL-/Gateway-Nachweise abgesichert. Produktionsdaten werden nicht manuell verändert, um Fehlerfälle künstlich nachzustellen.

Es werden keine Tokens, Discord-IDs, Rohshares oder personenbezogenen Testdaten in diesem Dokument erfasst.

## Akzeptanzmatrix 1–60

Die 60 Fälle aus `docs/requirements/achievements.md` bilden die verbindliche Mindestmatrix. Die Referenzen benennen jeweils die passende Testebene; PostgreSQL-Integrationstests laufen ausschließlich im Profil `database-integration` gegen Testcontainers-PostgreSQL.

| Fälle | Automatisierter Nachweis |
|---|---|
| 1–3 | `AchievementDefinitionCatalogTest`: exakt 60 Definitionen, eindeutige Schlüssel und Anzeigenamen, Präfix-/Scope-/Regelvalidierung. |
| 4–6 | `AchievementEvaluatorTest`, zusätzlich `PostgresAchievementShareToDeliveryE2EIT` für den realen Erst-Share mit gleichzeitigem `GW: Dabei!`, `GW: Geschafft!` und `GW: Volltreffer`. |
| 7–10 | `AchievementEvaluatorTest`, `AchievementHistorySnapshotTest`, `StreakDayClassifierGameParticipationTest`: Korrekturen zählen nicht doppelt, Meilensteine und beide Serienfamilien an den exakten Schwellen. |
| 11–14 | `AchievementEvaluatorTest`, `StreakDayClassifierGameParticipationTest`: aktiver Fehltag, Fehlschlag, Deaktivierung/Reaktivierung und historische spielbezogene Teilnahme. |
| 15–17 | `AchievementEvaluatorTest`: kumulative Doppeltage, erfolgreiche Doppeltage und spielweise gezählte Gesamterfahrung. |
| 18–26 | `AchievementEvaluatorTest`: Last-Chance-Regeln, Durchmarsch/Endgegner mit und ohne Boarddetails sowie die drei Cross-Game-Spezialfälle ausschließlich aus kanonischen Werten. |
| 27–33 | `AchievementEvaluatorTest`: Déjà-vu und Pleiten-Hattrick einschließlich Lücken- und Unterbrechungssemantik. |
| 34–37 | `AchievementEvaluatorTest`: `<07:00`, exakt `07:00`, `>=23:00` sowie Sommer-/Winterzeit über `Europe/Berlin`. |
| 38–40 | `AchievementReconciliationServiceTest`, `PostgresAchievementReconciliationIT`, `PostgresAchievementStaleReconciliationIT`, `PostgresAchievementPersistenceStoreIT`: Replay/Retry/Restart, konkurrierende Erstvergabe und stale Reconciliation. |
| 41–43 | `AchievementReconciliationServiceTest`, `PostgresAchievementReconciliationIT`, `PostgresAchievementBootstrapIT`: Invalidierung, append-only Historie ohne öffentliche Revocation und spätere Reaktivierung. |
| 44–45 | `PostgresAchievementShareToDeliveryE2EIT`, `AchievementAnnouncementRendererTest`: mehrere Unlocks desselben Shares werden in genau einem Live-Batch mit Name und Beschreibung ausgeliefert. |
| 46–48 | `PostgresAchievementBootstrapIT`, `AchievementEvaluatorTest`: Live-Gating bis Bootstrap-Erfolg, dieselben Fachregeln und historisch korrektes `earned_on`. |
| 49–52 | `PostgresAchievementBootstrapIT`, `PostgresAchievementPersistenceStoreIT`, `AchievementAnnouncementRendererTest`: exakt eine Introduction pro Teilnehmer/Definitionsversion, vollständiger Inhalt ohne Scope-Gruppierung und restartfähige Idempotenz. |
| 53–59 | `AchievementsQueryServiceTest`, `DiscordAchievementsCommandListenerTest`, `AchievementsOverviewEmbedRendererTest`, `PostgresAchievementsQueryIT`: Self/Other, exakte Scope-Filter, nur aktive Awards, read-only ohne History-Scan und vollständig ephemere Pagination. |
| 60 | `AchievementAnnouncementRendererTest`, `AchievementsOverviewEmbedRendererTest`: fehlendes Custom Emoji fällt auf das Unicode-Fallback zurück, ohne die Achievement-Identität zu verändern. |

## Nachtrag `/achievement-list`

Der ergänzende self-only Command ist in `docs/requirements/achievement-list.md` spezifiziert. Automatisiert abgedeckt sind:

- `AchievementCatalogQueryServiceTest`: alle 60 Definitionen in Katalogreihenfolge; ausschließlich `ACTIVE` ergibt `✅`, fehlend/invalidiert ergibt `❌`,
- `AchievementCatalogEmbedRendererTest`: vollständige 60er-Ausgabe mit Name/Beschreibung/Emoji ohne Fortschrittswerte innerhalb einer ephemeren Discord-Interaction und der 10-Embed-/6000-Zeichen-Grenze,
- `DiscordAchievementCatalogCommandListenerTest`: self-only, keine Optionen, ephemeral und mention-sicher,
- `DiscordRecordsCommandRegistrationTest`: zentrale Registrierung von `/achievement-list` genau einmal,
- `PostgresAchievementsQueryIT`: echter PostgreSQL-Read ohne Player-/Event-Seiteneffekte.

## Zusätzliche End-to-End- und Härtungsnachweise

### Kanonischer Share bis zur Achievement-Delivery

`PostgresAchievementShareToDeliveryE2EIT` führt einen echten GridWords-Share durch `GridWordsShareParser` und `ProcessSharedResultService` über die realen PostgreSQL-Adapter, `AchievementResultLifecycle`, History-Query, Evaluator, Reconciliation, Award-/Event-/Announcement-Persistenz und den Delivery-Coordinator bis zu einem externen Message-Gateway.

Der Test weist zusätzlich nach:

- Achievement-Reconciliation geschieht nach dauerhafter Ergebnisablage und vor kanonischem Discord-I/O,
- der erste erfolgreiche `1/6`-Share erzeugt drei aktive Awards und drei `UNLOCKED`-Events,
- genau ein `LIVE_UNLOCK_BATCH` enthält alle drei Events,
- die ausgelieferte Nachricht enthält Teilnehmername, Achievement-Namen und Beschreibungen,
- die persistierte Announcement-Projektion endet mit bestätigter Message-ID in `SYNCHRONIZED`.

### Bootstrap, Korrektur und Konkurrenz

`PostgresAchievementBootstrapIT` deckt in echtem PostgreSQL ab:

- aktive und inaktive historische Teilnehmer,
- Restart nach Teilfortschritt ohne doppelte Introductions,
- Ergebnisverarbeitung während laufendem Bootstrap ohne vorzeitige Live-Projektion,
- stilles Recovery von `RESULT_STORED`,
- Korrektur-Invalidierung ohne Revocation-Projektion,
- die konkurrierende Bootstrap-Endkante,
- technische Bootstrap-Fehler als `UNKNOWN`/retrybar statt als fachlichen Erfolg.

`PostgresAchievementStaleReconciliationIT` erzwingt zusätzlich den Race `alte solved-Auswertung -> neuere Korrektur/Invalidierung -> Fortsetzung der alten Auswertung`; die veraltete Auswertung darf den neueren Zustand nicht reaktivieren.

### Delivery, Retry, Restart und Send-vor-ACK

`PostgresAchievementAnnouncementDeliveryRecoveryIT` verbindet den realen PostgreSQL-Announcement-Store mit dem echten `AchievementAnnouncementDeliveryCoordinator` und ersetzt ausschließlich die externe Discord-Grenze durch ein kontrollierbares Gateway. Abgedeckt sind:

- serverseitig erfolgreicher Create mit lokal verlorenem ACK: persistierter Retry entdeckt die bestehende Nachricht anhand der stabilen Discord-Nonce statt erneut dauerhaft zu publizieren,
- deterministische Auswahl einer Nachricht bei mehreren Artefakten derselben logischen Create-Operation und Löschung ausschließlich der Duplikatartefakte,
- Restart nach bereits persistierter Message-ID, aber vor `SYNCHRONIZED`: ID-basierter Abschluss ohne neues Create oder Discovery,
- konkurrierende Delivery-Worker gegen PostgreSQL: genau ein Worker erhält die Arbeit und genau eine externe Nachricht bleibt dauerhaft gültig.

`PostgresAchievementPersistenceStoreIT` ergänzt die DB-seitigen Fences für Bootstrap-/Announcement-Claims, Introduction-vor-Live-Reihenfolge und tokengebundene Revalidation/Suppression. `AchievementAnnouncementDeliveryCoordinatorTest` deckt Fehlerklassifikation, Heartbeat und fachliche Revalidation ab; `JdaAchievementAnnouncementMessageGatewayTest` prüft Nonce, Mentionschutz und Gateway-Vertrag.

### Discord-Grenzen der vollständigen Einführung

`AchievementAnnouncementRendererTest.historicalIntroductionKeepsCatalogOrderAndFitsAllSixtyAchievementsIntoOneMessage` rendert den realen vollständigen `achievements-v1`-Katalog mit allen 60 Namen und Beschreibungen. Der Test erzwingt:

- genau eine logische Discord-Nachricht,
- höchstens die zulässige Anzahl Embeds,
- Einhaltung der kombinierten Embed-Zeichengrenze,
- jedes Achievement exakt in stabiler Katalogreihenfolge,
- keine zusätzliche GW-/QW-/GW+QW-/Allgemein-Gruppierung,
- keine fachliche Textkürzung.

### Upgrade und bestehende Daten

`AchievementPersistenceMigrationIT` prüft Clean-Install und den produktionsrelevanten Upgradepfad von Schema 023 auf 024 mit echtem PostgreSQL. Vor dem Upgrade werden repräsentativ bestehende Daten für Spieler, spielbezogene Teilnahme, `game_result`, `submission`, Ausredenstatus, Ausredenkontext und `record_state` angelegt. Nach 024 müssen diese Daten unverändert lesbar sein und die fünf Achievement-Tabellen zusätzlich existieren.

Damit wird Inkrement 13 als additive Schemaerweiterung geprüft, nicht nur gegen eine leere Datenbank.

## Regression bestehender Funktionen

Die verpflichtenden Gesamtbuilds führen die vollständigen bestehenden Unit- und Integrationstests weiter aus. Damit bleiben insbesondere Parser, kanonische Ergebnisverarbeitung, Teilnahme, Reminder/Tagesstatus, Reports, Ausreden und Records Teil der Regression.

Der Workflow `Container image` ergänzt Image-Runtime, Shellcheck sowie die isolierten Compose-, Backup-, Restore-, Resume- und Rollbackpfade.

## Reale Discord-Abnahme

### Lokal erfolgreich nachgewiesen

- [x] Bot mit einem noch nicht erfolgreich gebootstrappten `achievements-v1`-Stand gestartet.
- [x] PostgreSQL-Upgrade/Migration 024 gegen vorbereitete historische Daten erfolgreich.
- [x] Bootstrap erreicht `SUCCEEDED`.
- [x] Genau eine öffentliche historische Einführung für den vorbereiteten Teilnehmer.
- [x] Introduction ist genau eine Discord-Nachricht.
- [x] Drei erwartete rückwirkende GridWords-Awards (`GW: Dabei!`, `GW: Geschafft!`, `GW: Aller guten Dinge`).
- [x] Emoji, vollständiger Name und Beschreibung korrekt.
- [x] Keine internen Publication-IDs, Hashes oder Recovery-URLs sichtbar.
- [x] `/achievements` Self-Ansicht ephemeral und fachlich konsistent.
- [x] GridWords-/QuadWords-Filter korrekt.
- [x] Restart nach synchronisierter Introduction erzeugt kein Duplikat.

### Produktivrollout

- [x] Release 1.4.0 erfolgreich deployt.
- [x] Produktiver Start ohne Rollback abgeschlossen.
- [x] Produktiv-Smoke/Canary durch den Betreiber als bestanden bestätigt.
- [x] Während des Smoke-Tests kein dokumentiertes Abbruch-/Rollbackkriterium ausgelöst.

Die konkreten Canary- und Beobachtungskriterien stehen in `13-achievements-live-canary.md`.

## Technische und betriebliche Gates

Der finale Pre-Merge-Head von PR #103 war vollständig grün für:

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

Zusätzlich war der vollständige GitHub-Actions-Workflow `Container image` inklusive Image-Runtime, Shellcheck, Compose, Backup, Restore, Resume und Rollback grün.

## Abschluss

Inkrement 13 ist technisch, real und produktiv abgenommen. PR #103 ist nach `main` gemergt, Issue #94 ist abgeschlossen und Release 1.4.0 wurde erfolgreich ausgerollt. Der weitere Betrieb folgt den Diagnose-, Recovery- und Rollbackregeln aus `13-achievements-operations.md`.
