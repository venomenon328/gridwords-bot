# Implementierungsplan

Dieser Plan fasst die abgeschlossenen Produktinkremente zusammen und verweist für verbindliche Details auf Requirements, ADRs und Inkrementdokumente.

## Leitprinzipien

- Erst stabiler Build, dann Fachlogik.
- Parser und Regeln bleiben soweit möglich unabhängig von Discord und Datenbank.
- Der Standardbuild bleibt ohne Docker, PostgreSQL und Discord-Token ausführbar.
- Persistenzänderungen werden mit echtem PostgreSQL geprüft.
- Discord-I/O findet nicht innerhalb von Datenbanktransaktionen statt.
- Sichtbare Nachrichtenänderungen werden durch persistente Zustände, Claims, Leases, Retry und Recovery abgesichert.
- Originalnachrichten werden erst nach vollständig persistierter kanonischer Veröffentlichung gelöscht.
- Produktionsdeployments verwenden unveränderliche Containerimages, serverseitige Secrets und Backups vor Updates.
- GHCR-Veröffentlichungen erfolgen ausschließlich bewusst manuell.
- Große Inkremente werden in kleine, einzeln reviewbare Pakete zerlegt.

## Abgeschlossene Inkremente

### Inkrement 0 – Grundgerüst

**Status:** abgeschlossen; PR #1 gemergt.

Java 21, Spring Boot, JDA, Maven, externe Konfiguration und infrastrukturunabhängiger Build.

### Inkrement 1 – Share-Textparser

**Status:** abgeschlossen; PR #4 gemergt.

Deterministische GridWords- und QuadWords-Textparser.

### Inkrement 2 – Persistenzmodell

**Status:** abgeschlossen; PR #6 gemergt.

Liquibase, PostgreSQL, Submission-Zustände und Idempotenz.

### Inkrement 3 – Discord-Inbound

**Status:** abgeschlossen; PR #8 gemergt.

Gefilterter Discord-Listener, begrenzte Verarbeitung und Parse-/Persistenzpfad.

### Inkrement 4 – Kanonische GridWords-Nachricht

**Status:** abgeschlossen; PR #10 gemergt und real abgenommen.

Kanonisches Embed, Serien, persistierte Message-ID, Korrektur-Edit, Claims und Recovery.

### Inkrement 5 – Sichere GridWords-Ersetzung

**Status:** abgeschlossen; PR #12 gemergt und real abgenommen.

Quelllöschung erst nach persistierter Veröffentlichung sowie Retry- und Startup-Recovery.

### Inkrement 6 – QuadWords-Bildparser

**Status:** abgeschlossen; PR #14 gemergt und real abgenommen.

Reiner Java-Bildparser für vier normalisierte Boards ohne OCR oder ML.

### Inkrement 7 – Kanonische QuadWords-Konsolidierung

**Status:** abgeschlossen; PR #16 gemergt und real abgenommen.

Kanonische QuadWords-Nachricht, Korrektur-Edit, sichere Quelllöschung und gemeinsame Delivery-Sicherheitsmechanismen.

### Zwischeninkrement 7.1 – Kompaktes QuadWords-Layout

**Status:** abgeschlossen; Issue #17 und PR #18 gemergt.

Vier Boards als kompaktes 2×2-Layout mit stabiler horizontaler Ausrichtung.

### Zwischeninkrement 7.2 – Dynamische Spieler

**Status:** abgeschlossen und real abgenommen.

Dynamische Spielerprofile, historisch stabile Teilnahmezeiträume, Self-Service-/Admin-Commands und Reminder-Opt-out.

### Inkrement 8 – Tagesstatus und Erinnerungen

**Status:** abgeschlossen und real abgenommen.

Persistente Tagesstatusnachricht, vollständige Seriensemantik, Reminder und idempotente Reconciliation.

### Inkrement 9 – Produktionsdeployment und Betriebshärtung

**Status:** abgeschlossen; Issue #23 und PR #24 gemergt.

Nicht-Root-Container, PostgreSQL 16, internes Netz, Healthchecks, Backups, Restore, Resume, Rollback und explizites Deployment.

### Inkrement 10 – Wochen- und Monatsberichte

**Status:** abgeschlossen und automatisiert vollständig geprüft.

Abgeleitete, idempotent ausgelieferte Wochen- und Monatsberichte mit historischer Teilnehmer- und Seriensemantik.

### Zwischeninkrement 10.4 – Tagesabschluss und Channel-Bereinigung

**Status:** abgeschlossen; Issue #30 geschlossen und PR #35 gemergt.

Der tägliche Cleanup finalisiert den Vortag, pensioniert alte kanonische Ergebnis- und Reminder-Nachrichten und reconciliert den aktuellen Tagesstatus.

### Zwischeninkrement 10.5 – Interaktive Ergebnisdetails

**Status:** abgeschlossen; Issue #34 geschlossen und PR #36 gemergt; reale Discord-Abnahme bestanden.

Tagesstatusnachrichten enthalten spielbezogene Auswahlmenüs für ausschließlich lesende, ephemere Ergebnisdetails.

### Zwischeninkrement 10.6 – Spielbezogene Teilnahme

**Status:** abgeschlossen; Issue #39 geschlossen und PR #41 gemergt; automatisierte Prüfung, Produktionscontainer-Gate und reale Discord-Abnahme bestanden.

Spieler können unabhängig an GridWords, QuadWords, beiden Spielen oder keinem Spiel teilnehmen. Reminder, Serien, Tagesstatusmenüs und Berichtsnenner verwenden die jeweils passende historisch wirksame Teilnehmermenge.

Verbindlich:

- `docs/requirements/game-specific-participation.md`
- `docs/increments/10.6-game-specific-participation.md`
- `docs/operations/10.6-game-specific-participation-acceptance.md`
- ADR 0016

### Inkrement 11 – Kontextabhängige Ausreden

**Status:** abgeschlossen; Issue #42 geschlossen, PR #46 nach `main` gemergt, Release **1.2.0** veröffentlicht und produktiv ausgerollt.

Bei klar definierten auffälligen Ergebnissen kann der Ergebnisautor freiwillig drei private redaktionelle Ausreden öffnen, einmal den Stil wechseln, eine Ausrede auswählen oder verzichten. Nur der gewählte Text erscheint in derselben kanonischen Ergebnisnachricht. Ablauf, Cooldown, Wiederholungsschutz, Korrekturen und Recovery sind persistent abgesichert.

Verbindlich:

- `docs/requirements/excuses.md`
- `docs/increments/11-contextual-excuses.md`
- `docs/operations/11-contextual-excuses-acceptance.md`
- ADR 0017
- Issue #42

### Inkrement 12 – Rekorde und Rekordmeldungen

**Status:** abgeschlossen; Issue #58 geschlossen, alle zehn Pakete gemergt, technische und reale Discord-Abnahme bestanden und mit Release **1.3.0** produktiv ausgerollt.

Das Inkrement führt historisch korrekte persönliche, serverweite individuelle und gemeinsame Rekorde ein. Ergebnisrekorde unterscheiden wenigste Versuche, schnellste Lösung und langsamste erfolgreiche Lösung. Serienrekorde umfassen die bestehenden positiven Serien sowie X-Durststrecken und Tage ohne perfekten Tag. Öffentliche Meldungen werden aggregiert, zuverlässig ausgeliefert und nach Ergebniskorrekturen editiert oder gelöscht. Ein ephemerer `/records`-Command macht den aktuellen Stand überprüfbar.

Verbindlich:

- `docs/requirements/records.md`
- `docs/increments/12-records.md`
- `docs/operations/12-records-acceptance.md`
- ADR 0018
- Issue #58

### Inkrement 13 – Achievements

**Status:** abgeschlossen; Umbrella-Issue #86 und Paket #94 abgeschlossen, Gesamt-PR #103 nach `main` gemergt, Release **1.4.0** produktiv ausgerollt und Produktiv-Smoke/Canary bestanden.

Das Inkrement führt den kuratierten Katalog `achievements-v1` mit 60 stabilen, einmalig freischaltbaren Achievements ein. Vergaben werden aus der kanonischen Historie reconciled, korrekturfähig persistiert, bei Live-Freischaltungen aggregiert angekündigt und bei Einführung vollständig rückwirkend rekonstruiert.

`/achievements` zeigt die aktuell aktiven Achievements mit Self-/Other- und Game-Filter. `/achievement-list` zeigt self-only alle 60 Definitionen mit ausschließlich `✅`/`❌` als persönlichem Status und ohne quantitative Fortschrittsanzeige.

Der produktiv ausgerollte Achievement-Code basiert auf `main`-Commit:

`213fe15dcc59e46856ea9be7066161fdc473353a`

Verbindlich:

- `docs/requirements/achievements.md`
- `docs/requirements/achievement-list.md`
- `docs/increments/13-achievements.md`
- `docs/operations/13-achievements-acceptance.md`
- `docs/operations/13-achievements-operations.md`
- `docs/operations/13-achievements-live-canary.md`
- ADR 0020
- Issue #86

### Inkrement 14 – UX- und QoL-Stärkung bestehender Funktionen

**Status:** abgeschlossen; Issues #105–#110 geschlossen, Paket-PRs #112–#117 und Gesamt-PR #111 gemergt, technische und reale Abnahme bestanden und mit Release **1.5.0** produktiv ausgerollt. Produktiv-Smoke/Canary bestanden.

Inkrement 14 erweitert vorhandene Oberflächen ohne neue fachliche Persistenzwahrheit:

- informative Tagesstatus-Dropdowns und direkte Spiel-Links,
- angereicherte ephemere Ergebnisdetails mit gewählter Ausrede, aktuellen Ergebnisrekorden und Spieltag-Achievements,
- verständliche zeitliche Teilnahmebestätigungen,
- Reminderstatus mit tatsächlich konfigurierten Zeiten,
- `/status` als persönliches Dashboard mit heutigem Zustand und allen fünf persönlichen Serien,
- fachliches `earnedOn` in `/achievements`,
- kombinierbare `game`-/`category`-/`status`-Filter in `/achievement-list`, weiterhin ohne quantitative Progressanzeige,
- Scopefilter in `/records`,
- Achievement-Anzahlen und gültige Record-Highlights in Wochen- und Monatsberichten.

Die Read-only-Pfade bleiben read-only und verwenden vorhandene kanonische Daten beziehungsweise materialisierte Fachprojektionen. Für das gesamte Inkrement war **keine neue Liquibase-Migration** notwendig; der produktive Schema-Endstand bleibt Migration 024.

Technische Abschlussabnahme:

- finaler 14.5-Paket-Head `f00d56bbc22181395ad2ea9c1703bf3d77051d18`,
- Standardbuild, PostgreSQL-Integration und Migration/Upgrade grün,
- vollständiger Produktionsimage-/Compose-/Backup-/Restore-/Resume-/Rollbackpfad grün,
- realer Discord-/Report-Smoke bestanden,
- Gesamt-PR #111 nach `main` gemergt.

Der tatsächlich produktiv ausgerollte 1.5.0-Anwendungscode basiert auf `main`-Commit:

`e06fc8f9dfb4645c0db5cd3532c1eb7b8289f784`

Verbindlich:

- `docs/requirements/ux-qol.md`
- `docs/increments/14-ux-qol.md`
- `docs/operations/14-ux-qol-acceptance.md`
- Umbrella-Issue #105
- Gesamt-PR #111

### Release 1.5.1 – Report-Layout-Nacharbeit

**Status:** abgeschlossen; Issue #119 geschlossen, PR #123 nach `main` gemergt, technische und reale Discord-Abnahme bestanden, Release **1.5.1** veröffentlicht und produktiv ausgerollt. Produktiv-Canary bestanden.

Die Patch-Nacharbeit verbessert ausschließlich die sichtbare Darstellung der bestehenden Wochen- und Monatsberichte:

- kompakte Reporttitel und klar getrennte Spielerblöcke,
- verdichtete GridWords-/QuadWords-Zeilen mit `✅`/`❌`/`⬜`,
- kompakte persönliche und gemeinsame Serienwerte,
- Statistik- und Highlightteil als getrennte persistierte Reportseiten,
- atomare zweizeilige Record-Highlights mit globaler deterministischer Pagination,
- eng begrenzter Montag-Startup-Refresh für den zuletzt fälligen erfolgreichen Wochenreport innerhalb des bereits bestehenden Catch-up-Fensters.

Die Reportfachwerte, Record-/Achievement-Selektion, Snapshotsemantik außerhalb dieses expliziten Rolloutpfads und das Datenbankschema bleiben unverändert. Der produktive Schema-Endstand bleibt Migration 024.

Technische und reale Abnahme:

- Implementierungshead `5c3937cb92d69224755b54e9a712051df5c5361e`,
- finaler Abnahmehead vor Merge `7313d69664fc0454683ecd197b92e49a24616e2b`,
- Standardbuild, PostgreSQL-Integration und Migration/Upgrade grün,
- Produktionsimage-/Runtime- sowie Compose-/Backup-/Restore-/Resume-/Rollbackregression grün,
- realer Discord-Smoke mit altem 1.5.0-Wochenreport und erfolgreichem Montag-Refresh bestanden,
- Produktivdeployment und Live-Canary bestanden.

Der tatsächlich produktiv ausgerollte 1.5.1-Anwendungscode basiert auf `main`-Commit:

`832235f1ccc47900494e04fe5535e39194b70354`

Verbindlich:

- `docs/requirements/report-layout.md`
- `docs/increments/1.5.1-report-layout.md`
- `docs/operations/1.5.1-report-layout-acceptance.md`
- Issue #119
- PR #123

## Versionsstände

### Version 1.0.x

Grundlegender produktionsfähiger Bot mit Parsern, kanonischen Ergebnisnachrichten, dynamischen Spielern, Tagesstatus, Reminder, Cleanup und Reports.

### Version 1.1.0

Spielbezogene Teilnahme an GridWords, QuadWords oder beiden Spielen.

### Version 1.2.0

Kontextabhängige Ausreden mit privater Auswahl und Übernahme des gewählten Textes in die kanonische Ergebnisnachricht.

**Status:** veröffentlicht und produktiv deployt.

### Version 1.3.0

Rekorde, Serienrekorde, aggregierte Rekordmeldungen sowie `/records`.

**Status:** veröffentlicht und produktiv deployt.

### Version 1.4.0

60 historisch rekonstruierbare Achievements, aggregierte Live-Freischaltungen, `/achievements` und `/achievement-list`.

**Status:** veröffentlicht, produktiv deployt und durch den Live-Smoke/Canary bestätigt.

### Version 1.5.0

UX-/QoL-Stärkung bestehender Funktionen gemäß Inkrement 14.

**Status:** veröffentlicht, produktiv deployt und durch den Produktiv-Smoke/Canary bestätigt.

Produktiver Anwendungscode: `e06fc8f9dfb4645c0db5cd3532c1eb7b8289f784`.

### Version 1.5.1

Kompaktes, klar gegliedertes Wochen-/Monatsreport-Layout mit eigener Highlightseite und idempotentem Montag-Startup-Refresh innerhalb des bestehenden Catch-up-Fensters.

**Status:** veröffentlicht, produktiv deployt und durch den Produktiv-Smoke/Canary bestätigt.

Produktiver Anwendungscode: `832235f1ccc47900494e04fe5535e39194b70354`.

## Obsolet gewordene frühere Roadmap-Platzhalter

Die früher vorgemerkten Folgeinkremente

- „Statistik- und Konfigurations-Commands“ und
- „Regelbasierte Kommentare“ als generischer Sammelumfang

werden nicht weiterverfolgt. Sie waren keine umgesetzten oder abgenommenen Anforderungen. Einzelne Ideen daraus können nur über neue, ausdrücklich priorisierte Issues mit eigener Fachentscheidung zurückkehren.

## Aktive Roadmap

Derzeit ist **kein weiteres Produktinkrement verbindlich freigegeben**. Neue Funktionen werden erst nach separater Priorisierung über eigene Issues und Paketplanung aufgenommen.

Spätere Achievement-Erweiterungen, insbesondere persönliche Rekord-, Durchschnitts-, Verbesserungs- oder Schwierigkeits-Achievements, benötigen eine eigene fachliche Entscheidung. Falls rekordbezogene Achievements ergänzt werden, dürfen sie auf den in Inkrement 12 eingeführten gültigen Rekordereignissen aufbauen und keine Rekordlogik duplizieren.

## Definition of Done für künftige Inkremente

- Issue- und Paketabnahmekriterien erfüllt,
- Standardbuild grün,
- bei Persistenzumfang PostgreSQL-Profil grün,
- GitHub Actions vollständig grün,
- keine Secrets,
- Dokumentation aktuell,
- notwendiger manueller Discord-Smoke-Test erfolgreich,
- bei Betriebsänderungen Backup-, Restore-, Upgrade- und Rollbackweg geprüft,
- produktiver Release erst nach erfolgreicher RC-/Rolloutfreigabe.