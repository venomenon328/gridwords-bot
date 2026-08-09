# Inkrement 14 – UX-/QoL-Abnahme

**Status:** vorbereitet; Nachweise werden paketweise ergänzt  
**Stand:** 9. August 2026  
**Umbrella-Issue:** #105  
**Abschlusspaket:** #110

Dieses Dokument führt die Abnahme von Inkrement 14 zusammen. Es ist kein Ersatz für die Fachanforderungen in `docs/requirements/ux-qol.md`, sondern die prüfbare Zuordnung von Anforderungen zu automatisierten und realen Nachweisen.

## 1. Pflichtgates für den finalen Head

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

Zusätzlich vor Gesamtmerge:

- vollständiger `Container image`-Workflow beziehungsweise äquivalenter Produktionscontainer-Gate,
- Compose,
- Backup,
- Restore,
- Resume,
- Application-Rollback,
- realer Discord-Smoke auf separater Testanwendung/Testserver mit isolierter PostgreSQL-Testdatenbank.

## 2. Akzeptanzmatrix

Die Spalte `Nachweis` wird während der Umsetzung mit konkreten Testklassen, Workflow-Runs oder Smoke-Schritten befüllt.

| # | Bereich | Akzeptanzfall | Paket | Nachweis | Status |
|---:|---|---|---:|---|---|
| 1 | Tagesstatus | `✅`-Dropdownoption mit Ergebnis und Dauer | 14.1 | offen | ⬜ |
| 2 | Tagesstatus | `❌`-Dropdownoption mit `X/max` und Dauer | 14.1 | offen | ⬜ |
| 3 | Tagesstatus | `⬜`-Dropdownoption mit `Noch nicht eingereicht` | 14.1 | offen | ⬜ |
| 4 | Tagesstatus | spielbezogene Teilnehmermenge, Sortierung und Pagination unverändert | 14.1 | offen | ⬜ |
| 5 | Tagesstatus | beide Spiel-Link-Buttons auf neuen Nachrichten; kein Sonderrefresh alter Nachrichten | 14.1 | offen | ⬜ |
| 6 | Ergebnisdetails | SELECTED-Ausrede exakt; andere Ausredenzustände unsichtbar | 14.1 | offen | ⬜ |
| 7 | Ergebnisdetails | ausschließlich aktuelle vom konkreten Resultat gehaltene Ergebnisrekorde | 14.1 | offen | ⬜ |
| 8 | Ergebnisdetails | ACTIVE Awards mit `earnedOn = gameDate`, alle Scopes, nur Emoji + Name | 14.1 | offen | ⬜ |
| 9 | Ergebnisdetails | leere optionale Bereiche entfallen; Interaction bleibt read-only | 14.1 | offen | ⬜ |
| 10 | Teilnahme | Join/Activate und Leave/Deactivate erklären zeitliche Wirksamkeit eindeutig | 14.2 | offen | ⬜ |
| 11 | Reminder | Opt-out-Semantik verständlich; tatsächliche konfigurierte Zeiten sichtbar | 14.2 | offen | ⬜ |
| 12 | `/status` | Reihenfolge Heute → fünf Serien → Teilnahme/Reminder → letzte Einreichungen | 14.2 | offen | ⬜ |
| 13 | `/status` | heutige Zustände gelöst/ungelöst/offen/nicht teilgenommen korrekt | 14.2 | offen | ⬜ |
| 14 | `/achievements` | fachliches `earnedOn` statt technischem Erkennungszeitpunkt | 14.3 | offen | ⬜ |
| 15 | `/achievement-list` | kombinierbare `game`/`category`/`status`-Filter; Global nur unter Alle | 14.3 | offen | ⬜ |
| 16 | `/achievement-list` | weiterhin binär ohne quantitative Progressanzeige; neutraler Leerzustand | 14.3 | offen | ⬜ |
| 17 | `/records` | kombinierbarer Scope-Filter und unveränderte Fremdansichts-Autorisierung | 14.3 | offen | ⬜ |
| 18 | Reports | Achievement-Freischaltungen je Spieler nur als ACTIVE-Anzahl der Periode | 14.4 | offen | ⬜ |
| 19 | Reports | alle geeigneten gültigen Rekordverbesserungen vollständig; keine Ties/Near Misses/stillen Origins | 14.4 | offen | ⬜ |
| 20 | Reports | Crossing+Finish derselben Serienquelle in einer Periode nicht doppelt; Pagination ohne Kürzung | 14.4 | offen | ⬜ |
| 21 | Read-only | Commands/Interactions lösen keine History-Scans, Evaluatoren, Reconciler oder fachlichen Writes aus | 14.1–14.4 | offen | ⬜ |
| 22 | Regression | Canonical-/Excuse-/Record-/Achievement-Delivery und bestehende Recoverypfade regressionsfrei | 14.5 | offen | ⬜ |
| 23 | Discord | alle sichtbar geänderten Pfade real auf Testserver abgenommen | 14.5 | offen | ⬜ |
| 24 | Operations | Standard-, PostgreSQL-, Migration- und Container-/Backup-/Restore-/Rollback-Gates grün | 14.5 | offen | ⬜ |

## 3. Realer Discord-Smoke

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

**Status:** noch nicht umgesetzt.

- Commit/PR: offen
- Standardbuild: offen
- PostgreSQL-Profil: offen
- Review: offen

### Paket 14.2 / Issue #107

**Status:** noch nicht umgesetzt.

- Commit/PR: offen
- Standardbuild: offen
- PostgreSQL-Profil: offen
- Review: offen

### Paket 14.3 / Issue #108

**Status:** noch nicht umgesetzt.

- Commit/PR: offen
- Standardbuild: offen
- PostgreSQL-Profil: offen
- Review: offen

### Paket 14.4 / Issue #109

**Status:** noch nicht umgesetzt.

- Commit/PR: offen
- Standardbuild: offen
- PostgreSQL-Profil: offen
- Review: offen

### Paket 14.5 / Issue #110

**Status:** wartet auf 14.1–14.4.

- Vollständige Gates: offen
- Discord-Smoke: offen
- Container-/Operations-Gate: offen
- Gesamt-PR: offen

## 5. Releasegrenze

Dieses Abnahmedokument endet mit dem mergefähigen Gesamtstand von Inkrement 14. RC-Build, Tag, GHCR-Publish und produktiver Rollout werden anschließend separat freigegeben und dürfen nicht vor erfolgreichem Abschluss dieser Matrix als erledigt dokumentiert werden.
