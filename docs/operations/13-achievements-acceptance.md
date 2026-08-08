# Abnahmeprotokoll Inkrement 13

Dieses Protokoll bündelt die technische End-to-End-Abnahme für Inkrement 13 / Issue #86 und Paket 13.8 / Issue #94. Es ist kein RC-, Release- oder Produktionsprotokoll.

## Status

**Technischer Stand:** umgesetzt und automatisiert gehärtet. Maßgeblich ist der finale Head von Draft-PR #103; die konkreten GitHub-Actions-Läufe werden zusätzlich in der PR-Beschreibung protokolliert.

**Reale Discord-Abnahme:** **ausstehend**. Sie wird nach Abschluss der technischen Härtung manuell auf dem separaten Testserver durchgeführt. Bis dahin bleibt PR #103 Draft und Inkrement 13 nicht mergefreigegeben.

**Release / RC / Produktion:** nicht Bestandteil von Paket 13.8. RC, Release und produktiver Rollout erfolgen erst nach erfolgreicher Discord-Abnahme und Merge in einem separaten Schritt.

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
| 44–45 | `PostgresAchievementShareToDeliveryE2EIT`, `AchievementAnnouncementRendererTest`: drei Unlocks desselben Shares werden in genau einem Live-Batch mit Name und Beschreibung ausgeliefert. |
| 46–48 | `PostgresAchievementBootstrapIT`, `AchievementEvaluatorTest`: Live-Gating bis Bootstrap-Erfolg, dieselben Fachregeln und historisch korrektes `earned_on`. |
| 49–52 | `PostgresAchievementBootstrapIT`, `PostgresAchievementPersistenceStoreIT`, `AchievementAnnouncementRendererTest`: exakt eine Introduction pro Teilnehmer/Definitionsversion, vollständiger Inhalt ohne Scope-Gruppierung und restartfähige Idempotenz. |
| 53–59 | `AchievementsQueryServiceTest`, `DiscordAchievementsCommandListenerTest`, `AchievementsOverviewEmbedRendererTest`, `PostgresAchievementsQueryIT`: Self/Other, exakte Scope-Filter, nur aktive Awards, read-only ohne History-Scan und vollständig ephemere Pagination. |
| 60 | `AchievementAnnouncementRendererTest`, `AchievementsOverviewEmbedRendererTest`: fehlendes Custom Emoji fällt auf das Unicode-Fallback zurück, ohne die Achievement-Identität zu verändern. |

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

- serverseitig erfolgreicher Create mit lokal verlorenem ACK: persistierter Retry entdeckt die bestehende Nachricht statt erneut dauerhaft zu publizieren,
- deterministische Auswahl einer Nachricht bei mehreren Artefakten derselben logischen Create-Operation und Löschung ausschließlich der Duplikatartefakte,
- Restart nach bereits persistierter Message-ID, aber vor `SYNCHRONIZED`: ID-basierter Abschluss ohne neues Create oder Discovery,
- konkurrierende Delivery-Worker gegen PostgreSQL: genau ein Worker erhält die Arbeit und genau eine externe Nachricht bleibt dauerhaft gültig.

`PostgresAchievementPersistenceStoreIT` ergänzt die DB-seitigen Fences für Bootstrap-/Announcement-Claims, Introduction-vor-Live-Reihenfolge und tokengebundene Revalidation/Suppression. `AchievementAnnouncementDeliveryCoordinatorTest` deckt Fehlerklassifikation, Heartbeat und fachliche Revalidation ab; `JdaAchievementAnnouncementMessageGatewayTest` prüft Nonce/Marker, Mentionschutz und Gateway-Vertrag.

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

Der bestehende Workflow `Container image` ergänzt Image-Runtime, Shellcheck sowie die isolierten Compose-, Backup-, Restore-, Resume- und Rollbackpfade. Paket 13.8 führt keine separate parallele Betriebslogik ein.

## Reale Discord-Abnahme – manuelle Checkliste

Die folgenden Punkte müssen auf dem separaten Testserver mit isolierter Testdatenbank erfolgreich durchgeführt und anschließend hier beziehungsweise in PR #103 protokolliert werden. Ein automatisierter Fake-/Mock-Nachweis ersetzt diese Abnahme ausdrücklich nicht.

### A. Historische Einführung

- [ ] Bot mit einem noch nicht erfolgreich gebootstrappten `achievements-v1`-Stand starten.
- [ ] Für jeden vorhandenen Teilnehmer erscheint genau eine öffentliche historische Einführung.
- [ ] Die Introduction ist **eine Discord-Nachricht**; mehrere Embeds innerhalb dieser Nachricht sind zulässig.
- [ ] Gesamtzahl und enthaltene Achievements entsprechen `/achievements` unmittelbar nach dem Bootstrap.
- [ ] Jedes enthaltene Achievement zeigt Emoji, vollständigen Namen und Beschreibung.
- [ ] Keine zusätzliche Gruppierung nach GridWords, QuadWords, GW+QW oder Allgemein.
- [ ] Bot während beziehungsweise nach Teilfortschritt neu starten; bereits synchronisierte Introductions werden nicht dupliziert.
- [ ] Falls praktikabel einen Teilnehmer ohne historische Awards prüfen: auch dieser erhält genau eine 0er-Introduction.

### B. Live-Freischaltung

- [ ] Nach erfolgreichem Bootstrap einen Share einreichen, der mehrere Achievements gleichzeitig freischaltet.
- [ ] Es erscheint genau **eine** öffentliche Live-Meldung für diesen Trigger.
- [ ] Teilnehmer, Anzahl, Emoji, vollständiger Achievement-Name und Beschreibung stimmen.
- [ ] Es entstehen keine unbeabsichtigten Discord-Mentions.
- [ ] Neustart nach synchronisierter Meldung erzeugt kein Duplikat.

### C. Korrektur / Invalidierung

- [ ] Einen bereits verarbeiteten Share so korrigieren, dass mindestens ein Achievement fachlich nicht mehr belegt ist.
- [ ] `/achievements` zeigt den korrigierten aktiven Zustand.
- [ ] Es erscheint **keine** öffentliche Aberkennungsnachricht.
- [ ] Eine bereits synchronisierte historische Unlock-Meldung wird wegen der späteren Invalidierung nicht editiert oder gelöscht.

### D. `/achievements`

- [ ] `/achievements` ohne Optionen zeigt das eigene Profil ephemeral.
- [ ] `/achievements user:...` zeigt ohne Adminrolle ein anderes Profil und erzeugt keinen sichtbaren öffentlichen Post.
- [ ] `game:GridWords` zeigt ausschließlich `GRIDWORDS`.
- [ ] `game:QuadWords` zeigt ausschließlich `QUADWORDS`.
- [ ] `Alle` kann GRIDWORDS, QUADWORDS, CROSS_GAME und GLOBAL enthalten.
- [ ] Invalidierte/gesperrte Achievements werden nicht angezeigt.
- [ ] Bei genügend vielen Awards funktioniert die vollständige ephemere Pagination.
- [ ] Unicode-Emojis werden korrekt dargestellt; bei nicht verfügbarem Custom Emoji bleibt das Unicode-Fallback sichtbar.

## Technische und betriebliche Gates

Verpflichtend für den finalen PR-Head:

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

Zusätzlich muss der vollständige GitHub-Actions-Workflow `Container image` inklusive Image-Runtime, Shellcheck, Compose, Backup, Restore, Resume und Rollback grün sein.

Die konkreten finalen Workflow-Nummern und der finale Commit-SHA werden in PR #103 dokumentiert. Dadurch verursacht das Eintragen eines neuen SHA in dieses Dokument keinen weiteren ungetesteten Dokumentationscommit.

## Merge- und Releaseentscheidung

Der technische Teil von Inkrement 13 kann nach grünen finalen Gates als **technisch abgenommen** gelten. Die Mergefreigabe setzt zusätzlich die oben dokumentierte reale Discord-Abnahme voraus.

Bis dahin gilt:

1. PR #103 bleibt Draft,
2. Issue #94 und Umbrella #86 bleiben offen,
3. kein RC, Tag, Release oder produktiver Rollout,
4. keine manuellen Datenbankänderungen zur Simulation erfolgreicher Delivery-/Bootstrap-Zustände.

Nach erfolgreicher Discord-Abnahme wird ausschließlich der Abnahmestatus ergänzt und der unveränderte technisch geprüfte Stand zur Mergeentscheidung verwendet.
