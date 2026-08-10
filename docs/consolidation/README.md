# Dokumentationskonsolidierung – Arbeitsplan

**Status:** Phase 1 – Inventur und Migrationsplan vorbereitet  
**Issue:** #125  
**Arbeitsbranch:** `docs/consolidate-documentation`  
**Ausgangsstand:** `ad0f206af52a26b4304881c8fb471f005f647ef2`  
**Produktiver Anwendungscode:** `832235f1ccc47900494e04fe5535e39194b70354`

Dieses Verzeichnis ist ein temporärer Arbeitsbereich für die Konsolidierung. Nach Abschluss des Pakets wird der Plan historisiert oder entfernt; er wird **nicht** Teil der dauerhaften normativen Dokumentstruktur.

## 1. Ziel

Die Dokumentation wird von einer historisch gewachsenen Folge aus Grundspezifikation, Nachträgen, Inkrementplänen und Abnahmen in eine Struktur überführt, in der jede Fragestellung genau eine offensichtliche aktuelle Quelle besitzt.

Die Konsolidierung ist semantikneutral:

- keine neue Produktfunktion,
- keine neue Architekturentscheidung,
- keine Änderung von Persistenz- oder Schedulersemantik,
- keine Änderung des Deploymentwegs,
- historische Information wird nicht vernichtet, sondern klar von aktueller Wahrheit getrennt.

Bei einem echten Widerspruch zwischen aktuell normativ verwendeten Quellen wird nicht geraten. Der Widerspruch wird als eigener Klärpunkt markiert und vor einer kanonischen Zusammenführung aufgelöst.

## 2. Zielstruktur

```text
README.md
AGENTS.md

docs/
├── README.md
├── product/
│   ├── overview.md
│   ├── results-and-publication.md
│   ├── participation.md
│   ├── streaks.md
│   ├── daily-status-and-reminders.md
│   ├── excuses.md
│   ├── records.md
│   ├── achievements.md
│   └── reports.md
│
├── architecture/
│   ├── overview.md
│   ├── data-and-consistency.md
│   ├── discord-and-delivery.md
│   ├── background-processing.md
│   └── production.md
│
├── adr/
│   ├── README.md
│   └── 0001-...0020-...
│
├── development/
│   ├── setup.md
│   ├── testing.md
│   ├── workflow.md
│   └── quadwords-fixtures.md
│
├── operations/
│   ├── README.md
│   ├── server-bootstrap.md
│   ├── deployment.md
│   ├── backup-restore.md
│   ├── records.md
│   ├── achievements.md
│   └── troubleshooting.md
│
└── history/
    ├── README.md
    ├── releases.md
    ├── increments/
    ├── releases/
    │   ├── 1.1.0/
    │   ├── 1.2.0/
    │   ├── 1.3.0/
    │   ├── 1.4.0/
    │   ├── 1.5.0/
    │   └── 1.5.1/
    └── legacy/

content/
└── excuses/
    ├── README.md
    ├── drafts/
    └── review/
```

Die endgültige physische Unterteilung darf in Phase 2/3 leicht angepasst werden, wenn dadurch weniger künstliche Dateien entstehen. Die **Dokumentrollen und Autoritätsgrenzen** sind dagegen verbindlich.

## 3. Autorität nach Dokumentrolle

### `docs/product/`

Beschreibt ausschließlich das heute geltende Nutzer- und Fachverhalten. Keine Formulierungen wie „ab Inkrement X überschreibt dieses Dokument Y“, sofern sie nur historische Entstehung erklären.

Wenn ein Verhalten geändert wird, wird das kanonische Produktdokument aktualisiert. Dauerhafte Override-Nachträge sollen nicht mehr auf `main` verbleiben.

### `docs/architecture/`

Beschreibt ausschließlich den aktuellen Systemaufbau, Abhängigkeitsgrenzen, Daten-/Konsistenzmodell und technische Laufzeitmuster. Inkrementgeschichte gehört nicht hierher.

### `docs/adr/`

Bewahrt Entscheidungen und ihre Historie. ADRs werden nicht zu einem aktuellen Architekturhandbuch umgeschrieben. Superseded-/Replacement-Beziehungen werden jedoch sichtbar gepflegt.

### `docs/development/`

Enthält heute ausführbare Entwicklungs-, Test- und Workflowanleitungen. Historische inkrementspezifische Smoke-Anleitungen gehören nicht hierher.

### `docs/operations/`

Enthält ausschließlich heute ausführbare Produktions-, Diagnose-, Backup-, Restore- und Recovery-Runbooks. Acceptance-Matrizen, Release Notes und vergangene Canary-Protokolle gehören nach `docs/history/`.

### `docs/history/`

Enthält nachvollziehbare Entstehungs- und Releasegeschichte. Inhalte dort sind ausdrücklich **nicht normativ** und dürfen nicht als aktuelle Produkt- oder Architekturanforderung verwendet werden.

### `content/`

Enthält versionierte redaktionelle oder buildrelevante Contentquellen. Diese Dateien können für Tools verbindlich sein, sind aber keine Systemdokumentation.

## 4. Kanonische Zusammenführungen

### Produkt

| Ziel | Hauptquellen |
|---|---|
| `product/overview.md` | gültige Systemgrenzen aus `anforderungsspezifikation.md`, README und aktuellem Produktstand |
| `product/results-and-publication.md` | gültige Share-/Parser-/kanonische Publication-Semantik aus Grundspezifikation, Architektur und ADRs |
| `product/participation.md` | `dynamic-player-model.md` + `game-specific-participation.md` + aktuelle UX-Bestätigungsregeln |
| `product/streaks.md` | `series-model.md` + spielbezogene Teilnahmepräzisierungen |
| `product/daily-status-and-reminders.md` | `daily-status-reminders.md` + aktuelle Teilnahme-/UX-Regeln |
| `product/excuses.md` | `excuses.md` + gültige Katalogumfangsregeln; redaktionelle Quellen bleiben unter `content/` |
| `product/records.md` | `records.md` + aktuelle `/records`-/Ergebnisdetail-/Reportregeln aus 1.5.0 |
| `product/achievements.md` | `achievements.md` + `achievement-list.md` + aktuelle 1.5.0-Read-View-Regeln |
| `product/reports.md` | `periodic-reports.md` + spielbezogene Teilnahme + 1.5.0-Highlights + `report-layout.md` |

### Architektur

`docs/architecture.md` wird nicht einfach verschoben, sondern gegen den produktiven Code und die akzeptierten ADRs in aktuelle Querschnittsdokumente zerlegt. Insbesondere müssen die heute real vorhandenen Domainbereiche `achievement`, `record`, `status` und `reporting` berücksichtigt werden.

### Betrieb

`requirements/production-deployment.md` wird in aktuelle Architektur-/Sicherheitsinvarianten und konkrete Operationsanleitungen aufgeteilt. Prozedurale Doppelungen mit `operations/deployment.md`, `backup-restore.md` und `server-bootstrap.md` werden entfernt.

## 5. Bekannte Alterungs- und Konfliktstellen

Diese Stellen sind in Phase 2 ausdrücklich zu bereinigen, ohne ihre historische Existenz zu verschleiern:

1. `anforderungsspezifikation.md`
   - genau zwei statisch konfigurierte Spieler,
   - alte konkrete Spieler-/Testannahmen,
   - Reminder 18:00/23:00 statt aktuellem 16:00/22:00-Default,
   - alte Version-3-Roadmap mit Statistik-/Konfigurationscommands und regelbasierten Kommentaren,
   - historische QuadWords-/Rohbild- und Versionsannahmen.
2. `dynamic-player-model.md` beschreibt noch das frühere globale Teilnahmezeitraummodell; `game-specific-participation.md` ist dafür heute die neuere Fachwahrheit.
3. `series-model.md`, `daily-status-reminders.md` und `periodic-reports.md` enthalten teilweise Formulierungen aus der Zeit vor spielbezogener Teilnahme und müssen mit `G(d)`, `Q(d)`, `U(d)`, `B(d)` konsistent zusammengeführt werden.
4. `ux-qol.md` ist ein Querschnitts-Nachtrag über viele Features; seine dauerhaften Regeln müssen in die jeweiligen Produktdokumente verteilt werden.
5. `report-layout.md` enthält die heute geltende sichtbare Reportdarstellung und die eng begrenzte Montag-Refresh-Ausnahme; beides gehört dauerhaft in `product/reports.md` beziehungsweise die passende Architektur-/Deliverybeschreibung.
6. `architecture.md` enthält historische „Inkrement X“-Abläufe und eine überholte Paketübersicht.
7. `development-guide.md` enthält noch einen manuellen Inkrement-6-Smoke mit inzwischen historischem QuadWords-Verhalten.
8. `operations/` mischt aktuelle Runbooks und historische Abnahmen/Release Notes.
9. ADR 0004 muss sichtbar als durch ADR 0010 hinsichtlich lokaler Dockerverfügbarkeit ersetzt markiert werden.
10. `README.md` und `AGENTS.md` duplizieren zu viel Detail und sollen nach Einführung von `docs/README.md` deutlich kürzer werden.

## 6. Phasen

### Phase 1 – Inventur

- [x] Zielstruktur festlegen.
- [x] Dokumentrollen und Autorität festlegen.
- [x] jede vorhandene Markdown-Datei klassifizieren; siehe `inventory.md`.
- [x] bekannte Alterungs-/Konfliktstellen benennen.
- [ ] Phase-1-Diff reviewen.

### Phase 2 – aktuelle Wahrheit erzeugen

- [ ] `docs/product/` aus den neuesten gültigen Quellen erzeugen.
- [ ] `docs/architecture/` gegen aktuellen Code und ADRs erzeugen.
- [ ] `docs/development/` aus dem heute gültigen Leitfaden ableiten.
- [ ] aktive Operations-Runbooks konsolidieren.
- [ ] semantischen Vergleich gegen aktuellen produktiven 1.5.1-Stand durchführen.

### Phase 3 – Historisierung und Content-Umzug

- [ ] alte Grundspezifikation nach `history/legacy/` verschieben.
- [ ] Inkrementpläne nach `history/increments/` verschieben.
- [ ] Acceptance-/Release-/Canary-Dokumente versionsbezogen historisieren.
- [ ] `docs/editorial/` nach `content/excuses/` verschieben.
- [ ] `tools/build_excuse_catalog.py` und zugehörige Tests auf neue Contentpfade umstellen.
- [ ] ADR-Index anlegen; Superseded-Status korrigieren.

### Phase 4 – Einstiegspunkte und Konsistenz

- [ ] `docs/README.md` als Dokumentationsindex erstellen.
- [ ] Root-README auf Produktüberblick, Quick Start und Doku-Einstieg reduzieren.
- [ ] `AGENTS.md` auf Arbeitsregeln und neue Dokumentautorität reduzieren.
- [ ] `implementation-plan.md` durch kompakte Releasehistorie/Aktive-Roadmap-Darstellung ersetzen oder historisieren.
- [ ] alle internen Markdownlinks und Toolpfade aktualisieren.
- [ ] keine alten kanonischen Pfade mehr aus aktivem Code/Docs referenzieren.

## 7. Tests und Abnahme

Dokumentationsbewegungen dürfen keine Produktsemantik ändern. Verbindliche Abschlussgates:

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Zusätzlich erforderlich:

- deterministische Prüfung aller relativen Markdownlinks,
- `python tools/build_excuse_catalog.py --check` nach Content-Umzug,
- Diffreview speziell auf verlorene Fachregeln statt nur auf Dateibewegungen,
- Vergleich von `AGENTS.md` und `docs/README.md` gegen die neue Autoritätsstruktur.

## 8. Merge-Regel

Der Umbau wird erst nach `main` gemergt, wenn die neue kanonische Struktur vollständig ist. Es soll auf `main` keinen Zwischenzustand geben, in dem alte normative Dokumente bereits entfernt, ihre aktuellen Inhalte aber noch nicht konsolidiert sind.
