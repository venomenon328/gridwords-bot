# Inkrement 14 – UX- und QoL-Stärkung

**Status:** fachlich spezifiziert und für Umsetzung vorbereitet  
**Stand:** 9. August 2026  
**Umbrella-Issue:** #105  
**Sammelbranch:** `feature/14-ux-qol`  
**Fachliche Grundlage:** [`../requirements/ux-qol.md`](../requirements/ux-qol.md)  
**Geplantes Release:** 1.5.0

## 1. Ziel

Inkrement 14 verbessert die Benutzerführung und Sichtbarkeit bereits vorhandener Bot-Funktionen. Es führt kein neues großes Fachsystem ein. Stattdessen werden Tagesstatus, Ergebnisdetails, Teilnahme-/Reminder-Antworten, Achievement-/Record-Commands, persönlicher Status und periodische Berichte gezielt erweitert.

Das Inkrement baut vollständig auf den produktiven Ständen der Inkremente 10–13 auf. Bestehende Fachlogik wird wiederverwendet; neue Anzeigeinformationen werden aus vorhandenen kanonischen Daten oder materialisierten Fachprojektionen gelesen.

## 2. Verbindlicher Umfang

Inkrement 14 umfasst:

- Statussymbol und Kurzinfo in den Ergebnis-Dropdowns der Tagesstatusnachricht,
- direkte Link-Buttons zu GridWords und QuadWords,
- gewählte Ausrede, aktuelle Ergebnisrekorde und Spieltag-Achievements in ephemeren Ergebnisdetails,
- verständlichere Teilnahmebestätigungen,
- klareren Reminderstatus einschließlich konfigurierter Zeiten,
- `/status` als persönliches Dashboard mit heutigem Zustand und allen fünf persönlichen Serien,
- `earnedOn` in `/achievements`,
- kombinierbare Filter für `/achievement-list`,
- Scope-Filter für `/records`,
- Achievement-Zahlen und vollständige Rekord-Highlights in Wochen-/Monatsberichten,
- End-to-End-Härtung und reale Discord-Abnahme der geänderten Pfade.

## 3. Architekturgrenzen

Für alle Pakete gilt:

1. `docs/requirements/ux-qol.md` ist der verbindliche Inkrement-14-Nachtrag.
2. Bestehende Spiel-, Teilnahme-, Serien-, Ausreden-, Rekord- und Achievement-Semantik wird nicht dupliziert oder neu interpretiert.
3. Read-only Commands/Interactions dürfen keine Player-, Award-, Record-, Excuse- oder Delivery-Writes auslösen.
4. Read-Views verwenden vorhandene materialisierte Fachprojektionen beziehungsweise schmale read-only Ports; kein History-Scan oder Evaluator/Reconciler nur für eine Anzeige.
5. JDA bleibt am Adapterrand; Application/Domain kennen keine JDA-Typen.
6. Discord-I/O findet niemals innerhalb einer Datenbanktransaktion statt.
7. Keine Discord-/Announcement-Texte als fachliche Datenquelle.
8. Keine neue Persistenzwahrheit; eine Liquibase-Migration ist nicht vorgesehen.
9. Falls ein Paket wider Erwarten eine Schemaänderung für notwendig hält, ist dies vor Umsetzung als konkrete Lücke nachzuweisen und darf nicht nur der Bequemlichkeit eines Read-Modells dienen.
10. Vorhandene Paging-, Mention-, Idempotenz-, Retry-, Recovery- und historische Teilnahmegrenzen bleiben verbindlich.
11. Keine generischen UI-, Query-, Dashboard-, Gamification-, Event- oder Messaging-Frameworks.
12. Keine Inhalte späterer Pakete vorziehen.

## 4. Branch- und PR-Modell

- Sammelbranch: `feature/14-ux-qol`.
- Pakete 14.1–14.4 erhalten je einen Arbeitsbranch auf Basis des jeweils aktuellen Sammelbranches.
- Paket-PRs zielen als Draft auf `feature/14-ux-qol`.
- Paket 14.5 startet erst nach Integration von 14.1–14.4.
- Nach Abschluss von 14.5 wird der kumulierte Sammelbranch als Gesamt-PR gegen `main` reviewt.
- Release Candidate, Tag, GHCR-Publish und produktiver Rollout erfolgen erst nach Merge und separater Release-/Deploymentfreigabe.

## 5. Paketübersicht

| Paket | Issue | Branch | Inhalt | Abhängigkeit |
|---|---:|---|---|---|
| 14.1 | #106 | `feature/14-1-daily-status-details-ux` | Tagesstatus-Dropdowns, Spiel-Link-Buttons, angereicherte Ergebnisdetails | – |
| 14.2 | #107 | `feature/14-2-personal-command-ux` | Teilnahmebestätigungen, Reminderstatus/-zeiten, `/status`-Dashboard | – |
| 14.3 | #108 | `feature/14-3-read-command-ux` | `earnedOn`, `/achievement-list`-Filter, `/records`-Scopefilter | – |
| 14.4 | #109 | `feature/14-4-report-highlights` | Achievement-/Record-Highlights in periodischen Berichten | bestehende produktive Record-/Achievement-Projektionen |
| 14.5 | #110 | `feature/14-5-ux-qol-hardening` | Gesamthärtung, reale Abnahme und Releasevorbereitung | 14.1–14.4 |

Pakete 14.1–14.4 sind fachlich weitgehend unabhängig. Gemeinsame kleine Read-Verträge dürfen nur dann in einem früheren Paket ergänzt werden, wenn sie dessen eigenen Umfang unmittelbar benötigen; keine vorauseilende Plattformbildung.

---

# Paket 14.1 – Tagesstatus und Ergebnisdetails UX

**Status:** vorbereitet; Umsetzung noch nicht begonnen  
**Issue:** #106  
**Arbeitsbranch:** `feature/14-1-daily-status-details-ux`  
**PR-Ziel:** `feature/14-ux-qol`

## Ziel

Die Tagesstatusnachricht wird als zentraler Einstiegspunkt gestärkt. Nutzer erkennen in den Select-Menüs bereits den Ergebnisstatus, können beide Spiele direkt öffnen und erhalten in den ephemeren Ergebnisdetails vorhandenen Zusatzkontext aus Ausreden, Rekorden und Achievements.

## Lieferumfang

### Informativere Select-Optionen

- Statussymbol `✅`, `❌` oder `⬜` im Label,
- Description mit `n/max · Dauer`, `X/max · Dauer` oder `Noch nicht eingereicht`,
- bestehende spielbezogene Teilnehmermenge, Sortierung und Pagination unverändert,
- transportneutrales View-Modell trägt die erforderlichen Statusdaten.

### Spiel-Link-Buttons

- `🟩 GridWords spielen`,
- `🟦 QuadWords spielen`,
- dieselben URLs wie in Remindern,
- reine Link-Buttons ohne Interaction/State,
- neue Tagesstatusnachrichten immer mit Buttons,
- bestehende heutige Nachricht nur bei ohnehin erfolgendem normalem Edit/Recreate,
- kein Sonderrefresh historischer oder unveränderter Nachrichten.

### Ergebnisdetails

Zusätzlich zur vorhandenen Ergebnis-/Boarddarstellung:

- SELECTED-Ausrede: exakt persistierter Text,
- aktuelle Rekorde: nur aktueller `record_state`, dessen GameResult-Quelle exakt die ausgewählte Ergebnis-ID ist,
- Achievements: alle aktuell ACTIVE Awards des Spielers mit `earnedOn == gameDate`, nur Emoji + Anzeigename,
- optionale leere Blöcke vollständig auslassen.

## Tests

Mindestens die in Issue #106 und `ux-qol.md` definierten Fälle, insbesondere:

- alle drei Dropdownzustände,
- 1–25 und 26–50 Teilnehmer,
- vollständige Komponenten bei Create/Edit/Recreate,
- SELECTED-/Nicht-SELECTED-Ausrede,
- aktueller persönlicher/serverweiter Ergebnisrekord versus gebrochener historischer Rekord,
- aktive/invalidierte Awards und Cross-Game/Global am Spieltag,
- strikt read-only auf echtem PostgreSQL,
- bestehende Interaction-Manipulations- und Restartfälle.

## Verifikation

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

## Nicht Bestandteil

- Rekord-/Achievement-Neuberechnung,
- historische Rekordchronik,
- Achievement-Kausalitätsanalyse,
- Achievement-Beschreibungen,
- Progressanzeigen,
- Reminder-/Status-/Reportänderungen späterer Pakete,
- Components-V2-Umbau.

## Definition of Done

- Issue #106 vollständig erfüllt,
- Standardbuild und PostgreSQL-Profil grün,
- keine Migration ohne nachgewiesene Lücke,
- Änderungen vollständig committen und pushen,
- Draft-PR gegen `feature/14-ux-qol` mit `Closes #106`,
- bis zur vollständigen Abnahme im Draft lassen.

---

# Paket 14.2 – Persönlicher Status, Teilnahme und Reminder UX

**Status:** vorbereitet; Umsetzung noch nicht begonnen  
**Issue:** #107  
**Arbeitsbranch:** `feature/14-2-personal-command-ux`

## Ziel

Teilnahme-/Reminder-Self-Service wird verständlicher und `/status` beantwortet zuerst den aktuellen Spielzustand.

## Lieferumfang

- Join/Activate: `ab heute aktiv` beziehungsweise idempotent `bereits aktiv`,
- Leave/Deactivate: heutigen Zustand und konkrete Wirksamkeit ab morgen eindeutig darstellen,
- Reminder on/off/status erklärt Mention versus Klartextname,
- `/reminders status` zeigt tatsächlich konfigurierte Zeiten,
- `/status` in Reihenfolge:
  1. Heute je Spiel,
  2. alle fünf persönlichen Serien,
  3. Teilnahmezeiträume + kompakter Reminderstatus,
  4. letzte Einreichungen.
- `/status` bleibt strikt read-only und verwendet die vorhandene Serien-/Tagessemantik.

## Verifikation

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

## Definition of Done

Issue #107 vollständig erfüllt; Draft-PR gegen Sammelbranch; keine neue Fach- oder Persistenzsemantik.

---

# Paket 14.3 – Achievement- und Record-Command UX

**Status:** vorbereitet; Umsetzung noch nicht begonnen  
**Issue:** #108  
**Arbeitsbranch:** `feature/14-3-read-command-ux`

## Ziel

Die bestehenden read-only Nachschlagecommands werden informativer und gezielter filterbar.

## Lieferumfang

- `/achievements`: `earnedOn` als fachliches Freischaltdatum,
- `/achievement-list`: kombinierbare Filter `game`, `category`, `status`,
- Global-Achievements nur bei `game=Alle`, kein eigener `Allgemein`-Choice,
- binäre ✅/❌-Semantik unverändert, kein Progress,
- `/records`: optionaler Scope `Alle|Persönlich|Serverweit|Gemeinsam`, kombinierbar mit bestehenden Filtern,
- bestehende Fremdansichts-Autorisierung unverändert,
- alle Commands read-only, ephemeral und mention-sicher.

## Verifikation

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

## Definition of Done

Issue #108 vollständig erfüllt; Draft-PR gegen Sammelbranch; keine History-/Evaluator-/Reconciliation-Arbeit pro Command.

---

# Paket 14.4 – Highlights in Wochen- und Monatsberichten

**Status:** vorbereitet; Umsetzung noch nicht begonnen  
**Issue:** #109  
**Arbeitsbranch:** `feature/14-4-report-highlights`

## Ziel

Die seit Inkrement 10 hinzugekommenen Achievement- und Record-Fakten werden kompakt in periodischen Berichten sichtbar, ohne Rankings oder neue Vergleichslogik.

## Lieferumfang

### Achievements

- je Reportteilnehmer nur Anzahl ACTIVE Awards mit `earnedOn` in der Periode,
- Spieler mit 0 weglassen,
- keine einzelnen Achievement-Namen.

### Rekorde

- ausschließlich VALID Events der Typen `RESULT_RECORD_BROKEN`, `SERIES_RECORD_CROSSED`, `RECORD_SERIES_FINISHED`,
- nur `processingOrigin.publicAnnouncementEligible()`,
- Periodendatum aus GameResult-Spieltag beziehungsweise StreakRecordValue-Enddatum,
- Gleichstand, Near Miss, Initialisierung und stille Origins auslassen,
- Crossing+Finish derselben StateKey/StreakRun-Kombination innerhalb derselben Periode auf Finish reduzieren,
- mehrere echte Ergebnisrekordverbesserungen nicht pauschal deduplizieren,
- jeden verbleibenden Rekord vollständig nennen,
- keine künstliche Maximalzahl; vorhandene deterministische Pagination erweitern/nutzen.

### Snapshot

- bestehende Report-Snapshot-/Catch-up-Semantik unverändert,
- keine neue persistierte Report-Fachwahrheit,
- keine Announcement-Texte als Datenquelle.

## Verifikation

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

## Definition of Done

Issue #109 vollständig erfüllt; Perioden- und Deduplizierungsfälle mit echtem PostgreSQL abgesichert; Draft-PR gegen Sammelbranch.

---

# Paket 14.5 – Gesamthärtung, Abnahme und Releasevorbereitung

**Status:** wartet auf 14.1–14.4  
**Issue:** #110  
**Arbeitsbranch:** `feature/14-5-ux-qol-hardening`

## Ziel

Kumulierten Stand ohne neuen Fachumfang technisch und real abnehmen und für den Gesamt-PR gegen `main` vorbereiten.

## Lieferumfang

- vollständige fachliche Konsistenzprüfung aller zehn Erweiterungen,
- gezielte Regressionshärtung,
- Abschlussdokumentation und Akzeptanzmatrix,
- realer Discord-Smoke aller sichtbar geänderten Pfade,
- kontrollierte Report-Abnahme,
- vollständige Maven-/PostgreSQL-/Migration-/Container-/Backup-/Restore-/Resume-/Rollback-Gates.

## Pflichtgates

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

Zusätzlich der vollständige vorhandene `Container image`-Workflow beziehungsweise äquivalente lokale/CI-Operationsgates.

## Definition of Done

- Issues #106–#109 integriert und ohne offene Blocker,
- Issue #110 vollständig erfüllt,
- `docs/operations/14-ux-qol-acceptance.md` vollständig befüllt,
- alle technischen und realen Abnahmen grün,
- Dokumentation synchron,
- Gesamt-PR von `feature/14-ux-qol` gegen `main` reviewbereit,
- Release/Produktivrollout weiterhin separater Schritt.

---

## 6. Nicht Bestandteil des Gesamtinkrements

- quantitative Achievement-Fortschritte,
- neue Achievements oder Rekordmetriken,
- Rankings/Leaderboards/Spieler der Woche,
- Reminderstatus pro Spiel,
- Trendvergleiche in Reports,
- manuelle Report-Commands,
- generische Frameworks,
- produktiver Release innerhalb der Paket-PRs.
