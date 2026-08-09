# Inkrement 14 – UX-/QoL-Abnahme

**Status:** Pakete 14.1–14.4 automatisiert abgenommen und integriert; Abschlussgates von 14.5 sowie realer Discord-Smoke noch ausstehend  
**Stand:** 9. August 2026  
**Umbrella-Issue:** #105  
**Abschlusspaket:** #110

Dieses Dokument führt die Abnahme von Inkrement 14 zusammen. Es ist kein Ersatz für die Fachanforderungen in `docs/requirements/ux-qol.md`, sondern die prüfbare Zuordnung von Anforderungen zu automatisierten und realen Nachweisen.

Statuslegende:

- ✅ automatisierter bzw. technischer Nachweis vollständig,
- 🟨 automatisierter Nachweis vorhanden; reale sichtbare Discord-Abnahme steht noch aus,
- ⬜ noch offen.

## 1. Pflichtgates für den finalen Head

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

Zusätzlich vor dem Gesamtmerge nach `main`:

- vollständiger `Container image`-Workflow,
- Produktionsimage und Nicht-Root-/Runtime-Inhalte,
- Compose,
- Backup,
- Restore,
- Resume,
- Application-Rollback,
- realer Discord-Smoke auf separater Testanwendung/Testserver mit isolierter PostgreSQL-Testdatenbank.

Die Pakete 14.1–14.4 wurden jeweils auf ihrem finalen Review-Head mit Standardbuild und PostgreSQL-Integration geprüft; zusätzlich liefen die Migration-/Upgrade-Gates im Review beziehungsweise auf den kumulierten Sammelständen. Der kumulierte Stand nach 14.4 (`d4bf877a4c5bdcb00467bb1fbe3c19a3c97ef819`) bestand Standardbuild, PostgreSQL-Integration und Migration/Upgrade. Die finalen 14.5-Gates werden auf dem Abschlussbranch erneut ausgeführt.

## 2. Akzeptanzmatrix

| # | Bereich | Akzeptanzfall | Paket | Automatisierter Nachweis | Status |
|---:|---|---|---:|---|---|
| 1 | Tagesstatus | `✅`-Dropdownoption mit Ergebnis und Dauer | 14.1 | `DailyStatusComponentRendererTest` | 🟨 |
| 2 | Tagesstatus | `❌`-Dropdownoption mit `X/max` und Dauer | 14.1 | `DailyStatusComponentRendererTest` | 🟨 |
| 3 | Tagesstatus | `⬜`-Dropdownoption mit `Noch nicht eingereicht` | 14.1 | `DailyStatusComponentRendererTest`, `JdaDailyStatusComponentsTest` | 🟨 |
| 4 | Tagesstatus | spielbezogene Teilnehmermenge, Sortierung und Pagination unverändert | 14.1 | `DailyStatusComponentRendererTest`, `JdaDailyStatusComponentsTest`, vorhandene Interaction-Tests | ✅ |
| 5 | Tagesstatus | beide Spiel-Link-Buttons auf neuen Nachrichten; kein Sonderrefresh alter Nachrichten | 14.1 | `DailyStatusComponentRendererTest`, `JdaDailyStatusComponentsTest`, `DailyStatusFingerprintTest` | 🟨 |
| 6 | Ergebnisdetails | SELECTED-Ausrede exakt; andere Ausredenzustände unsichtbar | 14.1 | `PostgresDailyResultDetailsQueryIT`, `DailyResultDetailsEmbedRendererTest` | 🟨 |
| 7 | Ergebnisdetails | ausschließlich aktuelle vom konkreten Resultat gehaltene Ergebnisrekorde | 14.1 | `PostgresDailyResultDetailsQueryIT` inkl. Ergebnisversions-/Definitionsversionsfall | 🟨 |
| 8 | Ergebnisdetails | ACTIVE Awards mit `earnedOn = gameDate`, alle Scopes, nur Emoji + Name | 14.1 | `PostgresDailyResultDetailsQueryIT`, `DailyResultDetailsEmbedRendererTest` | 🟨 |
| 9 | Ergebnisdetails | leere optionale Bereiche entfallen; Interaction bleibt read-only | 14.1 | `DailyResultDetailsServiceTest`, `DailyResultDetailsInteractionListenerTest`, `PostgresDailyResultDetailsQueryIT` | 🟨 |
| 10 | Teilnahme | Join/Activate und Leave/Deactivate erklären zeitliche Wirksamkeit eindeutig | 14.2 | `PlayerParticipationServiceTest`, `DiscordParticipationCommandListenerTest` | 🟨 |
| 11 | Reminder | Opt-out-Semantik verständlich; tatsächliche konfigurierte Zeiten sichtbar | 14.2 | `DiscordParticipationCommandListenerTest` | 🟨 |
| 12 | `/status` | Reihenfolge Heute → fünf Serien → Teilnahme/Reminder → letzte Einreichungen | 14.2 | `PersonalStatusEmbedRendererTest` | 🟨 |
| 13 | `/status` | heutige Zustände gelöst/ungelöst/offen/nicht teilgenommen korrekt | 14.2 | `PersonalStatusServiceTest`, `PersonalStatusEmbedRendererTest`, `PostgresPersonalStatusReadOnlyIT` | 🟨 |
| 14 | `/achievements` | fachliches `earnedOn` statt technischem Erkennungszeitpunkt | 14.3 | `AchievementsQueryServiceTest`, `AchievementsOverviewEmbedRendererTest` | 🟨 |
| 15 | `/achievement-list` | kombinierbare `game`/`category`/`status`-Filter; Global nur unter Alle | 14.3 | `AchievementCatalogQueryServiceTest`, `DiscordAchievementCatalogCommandListenerTest` | 🟨 |
| 16 | `/achievement-list` | weiterhin binär ohne quantitative Progressanzeige; neutraler Leerzustand | 14.3 | `AchievementCatalogEmbedRendererTest`, `PostgresAchievementsQueryIT` | 🟨 |
| 17 | `/records` | kombinierbarer Scope-Filter und unveränderte Fremdansichts-Autorisierung | 14.3 | `RecordsScopeFilterTest`, `DiscordRecordsCommandListenerTest`, `RecordsQueryReadOnlyInvariantTest` | 🟨 |
| 18 | Reports | Achievement-Freischaltungen je Spieler nur als ACTIVE-Anzahl der Periode | 14.4 | `PostgresAchievementsQueryIT`, `PeriodicReportUseCaseTest`, `PeriodicReportRendererTest` | 🟨 |
| 19 | Reports | alle geeigneten gültigen Rekordverbesserungen vollständig; keine Ties/Near Misses/stillen Origins | 14.4 | `PostgresAchievementsQueryIT`, `PeriodicReportRendererTest` | 🟨 |
| 20 | Reports | Crossing+Finish derselben Serienquelle in einer Periode nicht doppelt; Pagination ohne Kürzung | 14.4 | `PeriodicReportRendererTest` | 🟨 |
| 21 | Read-only | Commands/Interactions lösen keine History-Scans, Evaluatoren, Reconciler oder fachlichen Writes aus | 14.1–14.4 | `PostgresDailyResultDetailsQueryIT`, `PostgresPersonalStatusReadOnlyIT`, `RecordsQueryReadOnlyInvariantTest`, `PostgresAchievementsQueryIT` | ✅ |
| 22 | Regression | Canonical-/Excuse-/Record-/Achievement-/Report-Delivery und Recoverypfade regressionsfrei | 14.5 | vollständige bestehende Unit-/Application-Suite, u. a. `PeriodicReportDeliveryServiceTest`, `PeriodicReportDeliveryServiceTerminalReplayTest`, `PeriodicReportDeliveryServiceTimeFenceTest`; finales Gate in 14.5 | ✅ |
| 23 | Discord | alle sichtbar geänderten Pfade real auf Testserver abgenommen | 14.5 | siehe Abschnitt 3 | ⬜ |
| 24 | Operations | Standard-, PostgreSQL-, Migration- und Container-/Backup-/Restore-/Rollback-Gates grün | 14.5 | finaler CI- und `Container image`-Workflow des 14.5-PR | ⬜ |

## 3. Realer Discord-Smoke

Die folgenden Punkte werden bewusst nicht aus Unit-/Adaptertests als „real bestanden“ abgeleitet. Testdaten dürfen kontrolliert vorbereitet werden; Secrets und lokale IDs bleiben außerhalb des Repositories.

### 3.1 Tagesstatus und Ergebnisdetails

- [ ] `✅`-Option mit korrekter Kurzinfo
- [ ] `❌`-Option mit korrekter Kurzinfo
- [ ] `⬜`-Option
- [ ] GridWords-Link-Button
- [ ] QuadWords-Link-Button
- [ ] SELECTED-Ausrede in Detailansicht
- [ ] aktueller persönlicher Ergebnisrekord
- [ ] aktueller serverweiter Ergebnisrekord
- [ ] mehrere Achievements desselben Spieltags
- [ ] leere Zusatzbereiche fehlen
- [ ] Fremdspieler-Auswahl weiterhin erlaubt und ausschließlich ephemeral
- [ ] Restart ohne flüchtigen Interaction-Zustand

### 3.2 Teilnahme, Reminder und `/status`

- [ ] Join mit verständlicher Wirkung
- [ ] Leave mit `heute noch aktiv` / Wirksamkeit ab morgen
- [ ] Reminder on/off/status
- [ ] tatsächlich konfigurierte Reminderzeiten
- [ ] `/status`: heutiger Zustand
- [ ] `/status`: alle fünf Serien
- [ ] `/status`: Teilnahme/Reminder
- [ ] `/status`: letzte Einreichungen

### 3.3 Achievement- und Record-Commands

- [ ] `/achievements` mit historischem `earnedOn`
- [ ] `/achievement-list` ohne Filter vollständig
- [ ] `/achievement-list` mit repräsentativer Dreifach-Filterkombination
- [ ] `/achievement-list` leerer Filterzustand
- [ ] `/records scope:persönlich`
- [ ] `/records scope:serverweit`
- [ ] `/records scope:gemeinsam`
- [ ] kombinierter Record-Filter
- [ ] Nichtadmin-Fremdansicht weiterhin gesperrt

### 3.4 Reports

Kontrollierte Daten-/Clock-Konstellation ist zulässig.

- [ ] Achievement-Zahl je Spieler
- [ ] persönlicher Ergebnisrekord
- [ ] serverweiter Ergebnisrekord
- [ ] gemeinsamer Serienrekord
- [ ] Crossing+Finish derselben Serienquelle nur einmal im selben Report
- [ ] Gleichstand/Near Miss fehlt
- [ ] leerer Highlightbereich fehlt vollständig
- [ ] mehrere Rekorde werden ohne Kürzung paginiert

## 4. Paketnachweise

### Paket 14.1 / Issue #106

**Status:** umgesetzt, reviewt und in den Sammelbranch integriert.

- PR: #112
- finaler Review-Head: `03ba918…`
- Squash-Merge in Sammelbranch: `cb667a72cdf6fa7aabf0056938a00bf37a2b0071`
- Standardbuild: grün
- PostgreSQL-Integration: grün
- Migration/Upgrade auf Review-/Sammelstand: grün
- Review-Nacharbeiten: Fingerprint-Kompatibilität alter Tagesstatusnachrichten, vollständige Record-Quellidentität inklusive Ergebnisversion, UTF-16-sichere Select-Kürzung, echter SELECTED-Ausreden-Readtest

### Paket 14.2 / Issue #107

**Status:** umgesetzt, self-reviewt und in den Sammelbranch integriert.

- PR: #113
- finaler Paket-Head: `240752a73bf533ff54bd375af5bb0d9e8451338d`
- Squash-Merge in Sammelbranch: `ee4a5e8bc639c7bfaf215c8ddc278d4d16d9e854`
- Standardbuild: grün
- PostgreSQL-Integration: grün
- Migration/Upgrade: grün
- besonderer Nachweis: `/status` ist über `PostgresPersonalStatusReadOnlyIT` ohne Profil-/Participation-Mutation abgesichert

### Paket 14.3 / Issue #108

**Status:** umgesetzt, self-reviewt und in den Sammelbranch integriert.

- PR: #114
- finaler Paket-Head: `d0e3fc5bcfb118eaff8e52ad90dba3c48fb16410`
- Squash-Merge in Sammelbranch: `949115aa16fa4e4615bb17dbc7fb2cf95557c8ad`
- Standardbuild: grün
- PostgreSQL-Integration: grün
- Migration/Upgrade: grün
- keine neuen Persistenzqueries oder Migrationen

### Paket 14.4 / Issue #109

**Status:** umgesetzt, reviewt und in den Sammelbranch integriert.

- PR: #115
- finaler Review-Head: `88f2eb7a4babd290b4ec51c5359afa73b21947e5`
- Squash-Merge in Sammelbranch: `d4bf877a4c5bdcb00467bb1fbe3c19a3c97ef819`
- Standardbuild: grün
- PostgreSQL-Integration: grün
- Migration/Upgrade: grün
- Review-Nacharbeiten: Record-Origin-Eligibility auf `publicAnnouncementEligible()` zentralisiert, stillen Highlight-Wiring-Fallback entfernt, PostgreSQL-Nachweis um BACKFILL/Gleichstand/Invalidierung ergänzt

### Paket 14.5 / Issue #110

**Status:** technische Abschlussarbeiten laufen; realer Discord-Smoke folgt nach Merge des Hardening-Stands in den Sammelbranch.

- Arbeitsbranch: `feature/14-5-ux-qol-hardening`
- Ausgangsstand: `d4bf877a4c5bdcb00467bb1fbe3c19a3c97ef819`
- vollständige Maven-Gates: auf finalem 14.5-Head erneut erforderlich
- Container-/Operations-Gate: auf finalem 14.5-Head erforderlich
- Discord-Smoke: offen
- Gesamt-PR: #111 bleibt bis zum realen Smoke im Draft

## 5. Releasegrenze

Der Merge des technischen 14.5-Hardening-Stands in `feature/14-ux-qol` ist **keine** Freigabe für `main` und kein Release. Issue #110 und Gesamt-PR #111 bleiben bis zum dokumentierten realen Discord-/Report-Smoke offen beziehungsweise im Draft.

RC-Build, Tag, GHCR-Publish und produktiver Rollout werden anschließend separat freigegeben und dürfen vor erfolgreichem Abschluss dieser Matrix nicht als erledigt dokumentiert werden.
