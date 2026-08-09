# Inkrement 14 – UX-/QoL-Abnahme

**Status:** vollständig technisch, real und produktiv abgenommen  
**Stand:** 10. August 2026  
**Umbrella-Issue:** #105  
**Abschlusspaket:** #110  
**Release:** 1.5.0  
**Produktiver Anwendungscode:** `e06fc8f9dfb4645c0db5cd3532c1eb7b8289f784`

Dieses Dokument führt die Abnahme von Inkrement 14 zusammen. Es ist kein Ersatz für die Fachanforderungen in `docs/requirements/ux-qol.md`, sondern die prüfbare Zuordnung von Anforderungen zu automatisierten, realen und produktiven Nachweisen.

Statuslegende:

- ✅ Nachweis vollständig und abgenommen,
- ⬜ noch offen.

## 1. Technische Pflichtgates

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

Zusätzlich:

- vollständiger `Container image`-Workflow,
- Produktionsimage und Nicht-Root-/Runtime-Inhalte,
- Compose,
- Backup,
- Restore,
- Resume,
- Application-Rollback,
- realer Discord-/Report-Smoke auf separater Testanwendung/Testserver mit isolierter PostgreSQL-Testdatenbank.

Die Pakete 14.1–14.4 wurden jeweils auf ihrem finalen Review-Head mit Standardbuild und PostgreSQL-Integration geprüft; zusätzlich liefen die Migration-/Upgrade-Gates im Review beziehungsweise auf den kumulierten Sammelständen.

Der finale technische 14.5-Head `f00d56bbc22181395ad2ea9c1703bf3d77051d18` war vollständig grün:

- GitHub Actions `CI` Run **#1674** / Run-ID `31324466419`: Standardbuild, PostgreSQL-Integration und Migration/Upgrade grün,
- GitHub Actions `Container image` Run **#511** / Run-ID `31324466410`: Maven-Verifikation, Produktionsimage, Nicht-Root-/Runtime-Inhalte, Compose, Backup, Restore, Resume und Application-Rollback grün,
- PR #117 wurde anschließend als `29fc6c0ab45570bcb4e7c7800ca484a7635cb0ff` in `feature/14-ux-qol` integriert,
- der finale Feature-Head wurde vor Gesamtmerge erneut erfolgreich durch die Maven-Gates geprüft.

Inkrement 14 führte **keine neue Liquibase-Migration** ein. Der produktive Schema-Endstand bleibt Migration 024.

## 2. Akzeptanzmatrix

| # | Bereich | Akzeptanzfall | Paket | Nachweis | Status |
|---:|---|---|---:|---|---|
| 1 | Tagesstatus | `✅`-Dropdownoption mit Ergebnis und Dauer | 14.1 | `DailyStatusComponentRendererTest` + realer Smoke | ✅ |
| 2 | Tagesstatus | `❌`-Dropdownoption mit `X/max` und Dauer | 14.1 | `DailyStatusComponentRendererTest` + realer Smoke | ✅ |
| 3 | Tagesstatus | `⬜`-Dropdownoption mit `Noch nicht eingereicht` | 14.1 | `DailyStatusComponentRendererTest`, `JdaDailyStatusComponentsTest` + realer Smoke | ✅ |
| 4 | Tagesstatus | spielbezogene Teilnehmermenge, Sortierung und Pagination unverändert | 14.1 | `DailyStatusComponentRendererTest`, `JdaDailyStatusComponentsTest`, vorhandene Interaction-Tests | ✅ |
| 5 | Tagesstatus | beide Spiel-Link-Buttons auf neuen Nachrichten; kein Sonderrefresh alter Nachrichten | 14.1 | `DailyStatusComponentRendererTest`, `JdaDailyStatusComponentsTest`, `DailyStatusFingerprintTest` + realer Smoke | ✅ |
| 6 | Ergebnisdetails | SELECTED-Ausrede exakt; andere Ausredenzustände unsichtbar | 14.1 | `PostgresDailyResultDetailsQueryIT`, `DailyResultDetailsEmbedRendererTest` + realer Smoke | ✅ |
| 7 | Ergebnisdetails | ausschließlich aktuelle vom konkreten Resultat gehaltene Ergebnisrekorde | 14.1 | `PostgresDailyResultDetailsQueryIT` inkl. Ergebnisversions-/Definitionsversionsfall + realer Smoke | ✅ |
| 8 | Ergebnisdetails | ACTIVE Awards mit `earnedOn = gameDate`, alle Scopes, nur Emoji + Name | 14.1 | `PostgresDailyResultDetailsQueryIT`, `DailyResultDetailsEmbedRendererTest` + realer Smoke | ✅ |
| 9 | Ergebnisdetails | leere optionale Bereiche entfallen; Interaction bleibt read-only | 14.1 | `DailyResultDetailsServiceTest`, `DailyResultDetailsInteractionListenerTest`, `PostgresDailyResultDetailsQueryIT` + realer Smoke | ✅ |
| 10 | Teilnahme | Join/Activate und Leave/Deactivate erklären zeitliche Wirksamkeit eindeutig | 14.2 | `PlayerParticipationServiceTest`, `DiscordParticipationCommandListenerTest` + realer Smoke | ✅ |
| 11 | Reminder | Opt-out-Semantik verständlich; tatsächliche konfigurierte Zeiten sichtbar | 14.2 | `DiscordParticipationCommandListenerTest` + realer Smoke | ✅ |
| 12 | `/status` | Reihenfolge Heute → fünf Serien → Teilnahme/Reminder → letzte Einreichungen | 14.2 | `PersonalStatusEmbedRendererTest` + realer Smoke | ✅ |
| 13 | `/status` | heutige Zustände gelöst/ungelöst/offen/nicht teilgenommen korrekt | 14.2 | `PersonalStatusServiceTest`, `PersonalStatusEmbedRendererTest`, `PostgresPersonalStatusReadOnlyIT` + realer Smoke | ✅ |
| 14 | `/achievements` | fachliches `earnedOn` statt technischem Erkennungszeitpunkt | 14.3 | `AchievementsQueryServiceTest`, `AchievementsOverviewEmbedRendererTest` + realer Smoke | ✅ |
| 15 | `/achievement-list` | kombinierbare `game`/`category`/`status`-Filter; Global nur unter Alle | 14.3 | `AchievementCatalogQueryServiceTest`, `DiscordAchievementCatalogCommandListenerTest` + realer Smoke | ✅ |
| 16 | `/achievement-list` | weiterhin binär ohne quantitative Progressanzeige; neutraler Leerzustand | 14.3 | `AchievementCatalogEmbedRendererTest`, `PostgresAchievementsQueryIT` + realer Smoke | ✅ |
| 17 | `/records` | kombinierbarer Scope-Filter und unveränderte Fremdansichts-Autorisierung | 14.3 | `RecordsScopeFilterTest`, `DiscordRecordsCommandListenerTest`, `RecordsQueryReadOnlyInvariantTest` + realer Smoke | ✅ |
| 18 | Reports | Achievement-Freischaltungen je Spieler nur als ACTIVE-Anzahl der Periode | 14.4 | PostgreSQL-Periodenread, `PeriodicReportUseCaseTest`, `PeriodicReportRendererTest` + realer Smoke | ✅ |
| 19 | Reports | alle geeigneten gültigen Rekordverbesserungen vollständig; keine Ties/Near Misses/stillen Origins | 14.4 | PostgreSQL-Periodenread, `PeriodicReportRendererTest` + realer Smoke | ✅ |
| 20 | Reports | Crossing+Finish derselben Serienquelle in einer Periode nicht doppelt; Pagination ohne Kürzung | 14.4 | `PeriodicReportRendererTest` + realer Smoke | ✅ |
| 21 | Read-only | Commands/Interactions lösen keine History-Scans, Evaluatoren, Reconciler oder fachlichen Writes aus | 14.1–14.4 | `PostgresDailyResultDetailsQueryIT`, `PostgresPersonalStatusReadOnlyIT`, `RecordsQueryReadOnlyInvariantTest`, `PostgresAchievementsQueryIT` | ✅ |
| 22 | Regression | Canonical-/Excuse-/Record-/Achievement-/Report-Delivery und Recoverypfade regressionsfrei | 14.5 | vollständige Unit-/Application-Suite und finale CI-Gates | ✅ |
| 23 | Discord | alle sichtbar geänderten Pfade real auf Testserver abgenommen | 14.5 | Betreiber-Smoke vom 09.08.2026 | ✅ |
| 24 | Operations | Standard-, PostgreSQL-, Migration- und Container-/Backup-/Restore-/Rollback-Gates grün | 14.5 | CI #1674 / `31324466419`, Container #511 / `31324466410` | ✅ |
| 25 | Produktion | Release 1.5.0 veröffentlicht und produktiv ausgerollt | Release | produktiver Codecommit `e06fc8f9dfb4645c0db5cd3532c1eb7b8289f784` | ✅ |
| 26 | Produktion | unmittelbarer Produktiv-Smoke/Canary bestanden, kein Rollback ausgelöst | Release | Betreiberbestätigung vom 10.08.2026 | ✅ |

## 3. Realer Discord-/Report-Smoke vor dem Merge

Der Betreiber hat den vollständigen vereinbarten Smoke am 9. August 2026 als bestanden bestätigt. Es wurden keine Secrets, Discord-IDs oder personenbezogenen Testdaten dokumentiert.

### 3.1 Tagesstatus und Ergebnisdetails

- [x] `✅`-Option mit korrekter Kurzinfo
- [x] `❌`-Option mit korrekter Kurzinfo
- [x] `⬜`-Option
- [x] GridWords-Link-Button
- [x] QuadWords-Link-Button
- [x] SELECTED-Ausrede in Detailansicht
- [x] aktueller persönlicher Ergebnisrekord
- [x] aktueller serverweiter Ergebnisrekord
- [x] mehrere Achievements desselben Spieltags
- [x] leere Zusatzbereiche fehlen
- [x] Fremdspieler-Auswahl weiterhin erlaubt und ausschließlich ephemeral
- [x] Restart ohne flüchtigen Interaction-Zustand

### 3.2 Teilnahme, Reminder und `/status`

- [x] Join mit verständlicher Wirkung
- [x] Leave mit `heute noch aktiv` / Wirksamkeit ab morgen
- [x] Reminder on/off/status
- [x] tatsächlich konfigurierte Reminderzeiten
- [x] `/status`: heutiger Zustand
- [x] `/status`: alle fünf Serien
- [x] `/status`: Teilnahme/Reminder
- [x] `/status`: letzte Einreichungen

### 3.3 Achievement- und Record-Commands

- [x] `/achievements` mit historischem `earnedOn`
- [x] `/achievement-list` ohne Filter vollständig
- [x] `/achievement-list` mit repräsentativer Dreifach-Filterkombination
- [x] `/achievement-list` leerer Filterzustand
- [x] `/records scope:persönlich`
- [x] `/records scope:serverweit`
- [x] `/records scope:gemeinsam`
- [x] kombinierter Record-Filter
- [x] Nichtadmin-Fremdansicht weiterhin gesperrt

### 3.4 Reports

- [x] Achievement-Zahl je Spieler
- [x] persönlicher Ergebnisrekord
- [x] serverweiter Ergebnisrekord
- [x] gemeinsamer Serienrekord
- [x] Crossing+Finish derselben Serienquelle nur einmal im selben Report
- [x] Gleichstand/Near Miss fehlt
- [x] leerer Highlightbereich fehlt vollständig
- [x] mehrere Rekorde werden ohne Kürzung paginiert

## 4. Paketnachweise

### Paket 14.1 / Issue #106

- PR #112
- finaler Review-Head `03ba918…`
- Squash-Merge `cb667a72cdf6fa7aabf0056938a00bf37a2b0071`
- Standardbuild/PostgreSQL/Migration grün

### Paket 14.2 / Issue #107

- PR #113
- finaler Paket-Head `240752a73bf533ff54bd375af5bb0d9e8451338d`
- Squash-Merge `ee4a5e8bc639c7bfaf215c8ddc278d4d16d9e854`
- Standardbuild/PostgreSQL/Migration grün
- `/status`-Read-only-Nachweis: `PostgresPersonalStatusReadOnlyIT`

### Paket 14.3 / Issue #108

- PR #114
- finaler Paket-Head `d0e3fc5bcfb118eaff8e52ad90dba3c48fb16410`
- Squash-Merge `949115aa16fa4e4615bb17dbc7fb2cf95557c8ad`
- Standardbuild/PostgreSQL/Migration grün

### Paket 14.4 / Issue #109

- PR #115
- finaler Review-Head `88f2eb7a4babd290b4ec51c5359afa73b21947e5`
- Squash-Merge `d4bf877a4c5bdcb00467bb1fbe3c19a3c97ef819`
- Standardbuild/PostgreSQL/Migration grün

### Paket 14.5 / Issue #110

- PR #117
- finaler Paket-Head `f00d56bbc22181395ad2ea9c1703bf3d77051d18`
- Squash-Merge `29fc6c0ab45570bcb4e7c7800ca484a7635cb0ff`
- CI #1674 / `31324466419`: alle drei Maven-Gates grün
- Container #511 / `31324466410`: Produktionsimage und vollständige Operations-Gates grün
- Discord-/Report-Smoke: bestanden am 09.08.2026

### Gesamtinkrement

- Gesamt-PR #111
- finaler Feature-Head vor Merge: `9dec74c5ff92b4590470ee0df172563c98824667`
- `main`-Merge / produktiver Anwendungscode: `e06fc8f9dfb4645c0db5cd3532c1eb7b8289f784`
- Umbrella-Issue #105 und Abschlusspaket #110 geschlossen

## 5. Produktivsetzung 1.5.0

Release **1.5.0** wurde am 10. August 2026 produktiv ausgerollt.

Der tatsächlich deployte Anwendungscode ist:

`e06fc8f9dfb4645c0db5cd3532c1eb7b8289f784`

Der Rollout verwendete den verbindlichen Produktionsweg mit unveränderlichem SHA-Image, validiertem Pre-Deployment-Backup, Health-/Readiness-Prüfung und vorhandenem App-Rollbackpfad. Da Inkrement 14 keine neue Migration enthält, blieb der Schema-Endstand unverändert bis einschließlich Migration 024.

Der unmittelbare Produktiv-Canary wurde durch den Betreiber als bestanden bestätigt. Es wurde kein Abbruch- oder Rollbackkriterium ausgelöst.

Mindestens geprüft wurden die unmittelbar sichtbaren 1.5.0-Pfade wie persönlicher Status, Reminderstatus, Achievement-/Achievement-List-/Record-Commands und Tagesstatus-Details. Die normalen folgenden Tagesstatus- und periodischen Reportpfade bleiben zusätzlich durch die bereits bestandenen realen Vorab-Smokes sowie die automatisierten Persistenz-/Renderer-/Deliverytests abgesichert.

## 6. Abschluss

Inkrement 14 ist technisch, real und produktiv abgeschlossen. Release **1.5.0** ist der aktuelle Produktionsstand.

Nachgelagerte reine Dokumentationscommits auf `main` verändern nicht den dokumentierten produktiven Anwendungscode `e06fc8f9dfb4645c0db5cd3532c1eb7b8289f784`.