# Dokumentationsinventar und Migrationsklassifikation

**Stand:** 10. August 2026  
**Basis:** `main` auf `ad0f206af52a26b4304881c8fb471f005f647ef2`  
**Issue:** #125

Dieses Inventar klassifiziert den aktuellen Dokumentationsbestand vor dem Umbau. Die Zielpfade beschreiben die beabsichtigte dauerhafte Rolle; bei `MERGE` wird die aktuelle Datei nach Übernahme aller noch gültigen Inhalte nicht als normative Doppelquelle erhalten.

## Legende

- **KEEP** – bleibt grundsätzlich am aktuellen Ort; nur Link-/Statuspflege.
- **MERGE** – gültige Inhalte fließen in ein neues kanonisches Dokument; bisherige Quelle wird danach historisiert.
- **SPLIT** – Inhalte werden in mehrere aktuelle Dokumente nach Rolle aufgeteilt; bisherige Quelle wird historisiert.
- **HISTORY** – ist bereits primär historische Umsetzung/Abnahme und wird ohne normative Bedeutung verschoben.
- **RUNBOOK** – bleibt aktive Betriebsdokumentation, eventuell unter konsolidiertem Namen.
- **CONTENT** – wird aus `docs/` in versionierte Contentquellen verschoben; Toolpfade werden angepasst.
- **ENTRYPOINT** – bleibt zentraler Einstieg, wird aber deutlich gekürzt/neu ausgerichtet.

## 1. Repository-Einstiegspunkte außerhalb `docs/`

| Aktuell | Aktion | Ziel | Bemerkung |
|---|---|---|---|
| `README.md` | ENTRYPOINT | `README.md` | auf Produktüberblick, aktuellen Release, Quick Start und Link zu `docs/README.md` reduzieren |
| `AGENTS.md` | ENTRYPOINT | `AGENTS.md` | Arbeitsregeln und Dokumentautorität behalten; fachliche Detailduplikate entfernen |
| `fixtures/README.md` | KEEP | `fixtures/README.md` | kleiner lokaler Fixture-Einstieg; ergänzend auf `docs/development/quadwords-fixtures.md` verweisen |

## 2. Heutige Top-Level-Dateien unter `docs/`

| Aktuell | Aktion | Ziel | Bemerkung |
|---|---|---|---|
| `docs/anforderungsspezifikation.md` | SPLIT | aktuelle Regeln nach `docs/product/*`; Original nach `docs/history/legacy/anforderungsspezifikation-v0.5.md` | enthält mehrere überholte Annahmen und darf danach nicht normativ sein |
| `docs/architecture.md` | SPLIT | `docs/architecture/{overview,data-and-consistency,discord-and-delivery,background-processing,production}.md`; Original nach `docs/history/legacy/architecture-pre-consolidation.md` | aktuellen Code/ADRs gegenprüfen; Inkrementgeschichte entfernen |
| `docs/development-guide.md` | SPLIT | `docs/development/{setup,testing,workflow,quadwords-fixtures}.md`; Original nach `docs/history/legacy/development-guide-pre-consolidation.md` | historischen Inkrement-6-Smoke nicht in aktuelle Anleitung übernehmen |
| `docs/implementation-plan.md` | SPLIT | `docs/history/releases.md` + kompakter Roadmaphinweis in `docs/README.md`; Original nach `docs/history/legacy/implementation-plan-pre-consolidation.md` | kein zweiter Issue-Tracker auf `main` |

## 3. ADRs

Alle ADRs bleiben historische Entscheidungsdokumente unter `docs/adr/`. Neu kommt `docs/adr/README.md` als Index mit Status/Replacement-Beziehungen hinzu.

| Aktuell | Aktion | Ziel / Hinweis |
|---|---|---|
| `docs/adr/0001-modular-monolith-with-ports-and-adapters.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0002-idempotent-message-replacement.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0003-time-and-scheduling.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0004-docker-optional-local-development.md` | KEEP | Status sichtbar auf `superseded`/„ersetzt durch ADR 0010 hinsichtlich lokaler Dockerverfügbarkeit“ korrigieren |
| `docs/adr/0005-postgresql-persistence-model.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0006-owned-canonical-publication-claims.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0007-submission-publication-context.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0008-submission-supersession.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0009-gridwords-source-deletion-recovery.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0010-docker-available-local-development.md` | KEEP | bleibt aktuelle Entscheidung für lokale Dockerverfügbarkeit |
| `docs/adr/0011-transport-neutral-quadwords-image-parsing.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0012-daily-status-reminder-delivery.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0013-netcup-container-production.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0014-persistent-periodic-report-delivery.md` | KEEP | unverändert; Montag-Refresh ist spätere eng begrenzte Ergänzung im aktuellen Architekturtext, kein Umschreiben der ADR-Historie |
| `docs/adr/0015-persistent-channel-retention-and-day-close.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0016-game-specific-participation.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0017-persistent-excuse-selection.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0018-record-state-events-and-reconciled-announcements.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0019-migration-clean-install-gate.md` | KEEP | unverändert, Index ergänzen |
| `docs/adr/0020-achievement-state-reconciliation-and-delivery.md` | KEEP | unverändert, Index ergänzen |

## 4. Requirements

| Aktuell | Aktion | Kanonisches Ziel | Historischer Zielpfad / Hinweis |
|---|---|---|---|
| `docs/requirements/achievement-list.md` | MERGE | `docs/product/achievements.md` | `docs/history/legacy/requirements/achievement-list.md` |
| `docs/requirements/achievements.md` | MERGE | `docs/product/achievements.md` | `docs/history/legacy/requirements/achievements.md` |
| `docs/requirements/daily-status-reminders.md` | MERGE | `docs/product/daily-status-and-reminders.md` | `docs/history/legacy/requirements/daily-status-reminders.md` |
| `docs/requirements/dynamic-player-model.md` | MERGE | `docs/product/participation.md` | `docs/history/legacy/requirements/dynamic-player-model.md`; globales Teilnahmezeitraummodell nur historisch |
| `docs/requirements/excuse-catalog-volume.md` | MERGE + CONTENT | `docs/product/excuses.md` für fachlich relevante Umfangsregel; Katalogdetails nach `content/excuses/README.md` | historische Releasequelle optional unter `docs/history/releases/1.2.0/` |
| `docs/requirements/excuses.md` | MERGE | `docs/product/excuses.md` | `docs/history/legacy/requirements/excuses.md` |
| `docs/requirements/game-specific-participation.md` | MERGE | `docs/product/participation.md` | `docs/history/legacy/requirements/game-specific-participation.md`; für heutige Teilnahme maßgebliche Hauptquelle |
| `docs/requirements/periodic-reports.md` | MERGE | `docs/product/reports.md` | `docs/history/legacy/requirements/periodic-reports.md` |
| `docs/requirements/production-deployment.md` | SPLIT | technische Invarianten nach `docs/architecture/production.md`; prozedurale Regeln in aktuelle `docs/operations/*` | `docs/history/legacy/requirements/production-deployment.md` |
| `docs/requirements/records.md` | MERGE | `docs/product/records.md` | `docs/history/legacy/requirements/records.md` |
| `docs/requirements/report-layout.md` | MERGE | `docs/product/reports.md` + Deliveryteil in aktuelle Architektur | `docs/history/releases/1.5.1/specification.md` |
| `docs/requirements/series-model.md` | MERGE | `docs/product/streaks.md` | `docs/history/legacy/requirements/series-model.md` |
| `docs/requirements/ux-qol.md` | SPLIT | Regeln in `participation`, `daily-status-and-reminders`, `records`, `achievements`, `reports` und Ergebnis-/Publication-Dokument verteilen | `docs/history/releases/1.5.0/specification.md` |

## 5. Inkrement- und Paketpläne

Diese Dateien sind Umsetzungsgeschichte und werden nicht mehr als aktuelle Anforderungen referenziert.

| Aktuell | Aktion | Ziel |
|---|---|---|
| `docs/increments/06-quadwords-image-parser.md` | HISTORY | `docs/history/increments/06-quadwords-image-parser.md` |
| `docs/increments/07-canonical-quadwords-replacement.md` | HISTORY | `docs/history/increments/07-canonical-quadwords-replacement.md` |
| `docs/increments/07a-compact-quadwords-grid-layout.md` | HISTORY | `docs/history/increments/07a-compact-quadwords-grid-layout.md` |
| `docs/increments/07b-dynamic-player-participation.md` | HISTORY | `docs/history/increments/07b-dynamic-player-participation.md` |
| `docs/increments/08-daily-status-reminders.md` | HISTORY | `docs/history/increments/08-daily-status-reminders.md` |
| `docs/increments/09-production-deployment-hardening.md` | HISTORY | `docs/history/increments/09-production-deployment-hardening.md` |
| `docs/increments/10-periodic-reports.md` | HISTORY | `docs/history/increments/10-periodic-reports.md` |
| `docs/increments/10.1-boardless-quadwords.md` | HISTORY | `docs/history/increments/10.1-boardless-quadwords.md` |
| `docs/increments/10.2-shared-game-solved-streaks.md` | HISTORY | `docs/history/increments/10.2-shared-game-solved-streaks.md` |
| `docs/increments/10.3-personal-status-command.md` | HISTORY | `docs/history/increments/10.3-personal-status-command.md` |
| `docs/increments/10.4-day-close-reminder-retention-cleanup.md` | HISTORY | `docs/history/increments/10.4-day-close-reminder-retention-cleanup.md` |
| `docs/increments/10.5-interactive-result-details.md` | HISTORY | `docs/history/increments/10.5-interactive-result-details.md` |
| `docs/increments/10.6-game-specific-participation.md` | HISTORY | `docs/history/increments/10.6-game-specific-participation.md` |
| `docs/increments/11-contextual-excuses.md` | HISTORY | `docs/history/increments/11-contextual-excuses.md` |
| `docs/increments/12-records.md` | HISTORY | `docs/history/increments/12-records.md` |
| `docs/increments/13-achievements.md` | HISTORY | `docs/history/increments/13-achievements.md` |
| `docs/increments/14-ux-qol.md` | HISTORY | `docs/history/increments/14-ux-qol.md` |
| `docs/increments/1.5.1-report-layout.md` | HISTORY | `docs/history/releases/1.5.1/implementation.md` |
| `docs/increments/archive/11-contextual-excuses-package-plan.md` | HISTORY | `docs/history/increments/11-contextual-excuses-package-plan.md` |

## 6. Operations

### Aktive Runbooks

| Aktuell | Aktion | Ziel | Hinweis |
|---|---|---|---|
| `docs/operations/backup-restore.md` | RUNBOOK | `docs/operations/backup-restore.md` | behalten und Querverweise bereinigen |
| `docs/operations/deployment.md` | RUNBOOK | `docs/operations/deployment.md` | behalten; normative Doppelungen aus ehemaligem Requirement entfernen |
| `docs/operations/server-bootstrap.md` | RUNBOOK | `docs/operations/server-bootstrap.md` | behalten |
| `docs/operations/troubleshooting.md` | RUNBOOK | `docs/operations/troubleshooting.md` | behalten |
| `docs/operations/12-records-operations.md` | MERGE/RUNBOOK | `docs/operations/records.md` | mit `record-live-evaluation.md` konsolidieren |
| `docs/operations/record-live-evaluation.md` | MERGE/RUNBOOK | `docs/operations/records.md` | laufender produktiver Recordpfad, nicht historisieren ohne Übernahme |
| `docs/operations/13-achievements-operations.md` | RUNBOOK | `docs/operations/achievements.md` | aktuellen Inhalt übernehmen, releasebezogene Einleitung entfernen |

### Historische Abnahmen und Releases

| Aktuell | Aktion | Ziel |
|---|---|---|
| `docs/operations/10.6-game-specific-participation-acceptance.md` | HISTORY | `docs/history/releases/1.1.0/game-specific-participation-acceptance.md` |
| `docs/operations/11-contextual-excuses-acceptance.md` | HISTORY | `docs/history/releases/1.2.0/acceptance.md` |
| `docs/operations/12-records-acceptance.md` | HISTORY | `docs/history/releases/1.3.0/acceptance.md` |
| `docs/operations/12-records-release-notes.md` | HISTORY | `docs/history/releases/1.3.0/release-notes.md` |
| `docs/operations/13-achievements-acceptance.md` | HISTORY | `docs/history/releases/1.4.0/acceptance.md` |
| `docs/operations/13-achievements-live-canary.md` | HISTORY | `docs/history/releases/1.4.0/live-canary.md` |
| `docs/operations/13-achievements-release-notes.md` | HISTORY | `docs/history/releases/1.4.0/release-notes.md` |
| `docs/operations/14-ux-qol-acceptance.md` | HISTORY | `docs/history/releases/1.5.0/acceptance.md` |
| `docs/operations/1.5.1-report-layout-acceptance.md` | HISTORY | `docs/history/releases/1.5.1/acceptance.md` |

## 7. Redaktionelle Ausredenquellen

Diese Dateien sind buildrelevanter Content und werden gemeinsam mit `tools/build_excuse_catalog.py` verschoben/angepasst.

| Aktuell | Aktion | Ziel |
|---|---|---|
| `docs/editorial/excuse-catalog-manifest.md` | CONTENT | `content/excuses/README.md` |
| `docs/editorial/excuse-catalog-quality-guidelines.md` | CONTENT | `content/excuses/quality-guidelines.md` |
| `docs/editorial/excuse-catalog-review-pass-1.md` | CONTENT | `content/excuses/review/pass-1.md` |
| `docs/editorial/excuse-catalog-general-topic-map.md` | CONTENT | `content/excuses/review/general-topic-map.md` |
| `docs/editorial/excuse-catalog-draft.md` | CONTENT | `content/excuses/drafts/general.md` |
| `docs/editorial/excuse-catalog-draft-very-late-submission.md` | CONTENT | `content/excuses/drafts/very-late-submission.md` |
| `docs/editorial/excuse-catalog-draft-gridwords-last-attempt.md` | CONTENT | `content/excuses/drafts/gridwords-last-attempt.md` |
| `docs/editorial/excuse-catalog-draft-gridwords-very-slow.md` | CONTENT | `content/excuses/drafts/gridwords-very-slow.md` |
| `docs/editorial/excuse-catalog-draft-quadwords-very-slow.md` | CONTENT | `content/excuses/drafts/quadwords-very-slow.md` |
| `docs/editorial/excuse-catalog-draft-quadwords-single-board-collapse.md` | CONTENT | `content/excuses/drafts/quadwords-single-board-collapse.md` |
| `docs/editorial/excuse-catalog-draft-current-daily-outlier.md` | CONTENT | `content/excuses/drafts/current-daily-outlier.md` |

## 8. Neue dauerhafte Dateien

Folgende Dateien existieren heute noch nicht und sind Teil des Zielzustands:

```text
docs/README.md

docs/product/overview.md
docs/product/results-and-publication.md
docs/product/participation.md
docs/product/streaks.md
docs/product/daily-status-and-reminders.md
docs/product/excuses.md
docs/product/records.md
docs/product/achievements.md
docs/product/reports.md

docs/architecture/overview.md
docs/architecture/data-and-consistency.md
docs/architecture/discord-and-delivery.md
docs/architecture/background-processing.md
docs/architecture/production.md

docs/adr/README.md

docs/development/setup.md
docs/development/testing.md
docs/development/workflow.md
docs/development/quadwords-fixtures.md

docs/operations/README.md
docs/operations/records.md
docs/operations/achievements.md

docs/history/README.md
docs/history/releases.md

content/excuses/README.md
content/excuses/quality-guidelines.md
content/excuses/drafts/*
content/excuses/review/*
```

## 9. Querschnittsarbeiten, die kein einzelnes Dokument abbildet

1. **Markdown-Linkprüfung:** alle relativen Links nach Moves aktualisieren und automatisiert auf Existenz prüfen.
2. **Code-/Toolpfade:** `tools/build_excuse_catalog.py`, Katalogtests und eventuell AGENTS/README auf `content/excuses/` umstellen.
3. **Autoritätswechsel:** `AGENTS.md` darf nach Abschluss keine alte Override-Hierarchie mit `anforderungsspezifikation.md` als aktueller Grundwahrheit enthalten.
4. **ADR-Status:** ADR 0004 sichtbar als teilweise ersetzt markieren; ADR-Index pflegt alle 20 Entscheidungen.
5. **Releasehistorie:** `implementation-plan.md` nicht als zukünftigen zweiten Backlog fortführen; abgeschlossene Releases kompakt in `docs/history/releases.md`.
6. **Produktionsrelease:** der tatsächlich ausgerollte 1.5.1-Anwendungscode `832235f1ccc47900494e04fe5535e39194b70354` bleibt historische Releaseevidenz und wird durch spätere reine Dokucommits nicht umgedeutet.

## 10. Phase-1-Ergebnis

Die Inventur enthält damit:

- 83 Markdown-Dateien im heutigen `docs/`-Baum,
- die beiden zentralen Repository-Einstiegspunkte `README.md` und `AGENTS.md`,
- `fixtures/README.md` als zusätzliche entwicklungsnahe Dokumentdatei,
- klare Zielrollen für jede dieser Dateien.

Vor Phase 2 werden keine bestehenden normativen Pfade entfernt oder verschoben.
