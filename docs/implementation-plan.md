# Implementierungsplan

Dieser Plan fasst die Umsetzung der Anforderungsspezifikation zusammen und verweist für die verbindlichen Details auf die jeweiligen Requirements-, ADR- und Inkrementdokumente.

## Leitprinzipien

- Erst stabiler Build, dann Fachlogik.
- Parser und Regeln bleiben soweit möglich unabhängig von Discord und Datenbank.
- Der Standardbuild bleibt ohne Docker, PostgreSQL und Discord-Token ausführbar.
- Persistenzänderungen werden mit echtem PostgreSQL geprüft.
- Discord-I/O findet nicht innerhalb von Datenbanktransaktionen statt.
- Sichtbare Nachrichtenänderungen werden durch persistente Claims, Leases, Retry- und Recovery-Zustände abgesichert.
- Originalnachrichten werden erst nach vollständig persistierter kanonischer Veröffentlichung gelöscht.
- Produktionsdeployments verwenden reproduzierbare Containerimages, serverseitige Secrets und Backups vor Updates.
- GHCR-Veröffentlichungen erfolgen ausschließlich bewusst manuell; Pull Requests und Pushes auf `main` prüfen das Image nur.
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

Reiner Java-Bildparser für vier normalisierte Boards ohne OCR oder ML. Details: `docs/increments/06-quadwords-image-parser.md` beziehungsweise die zugehörigen Requirements und ADRs.

### Inkrement 7 – Kanonische QuadWords-Konsolidierung

**Status:** abgeschlossen; PR #16 gemergt und real abgenommen.

Kanonische QuadWords-Nachricht, Korrektur-Edit, sichere Quelllöschung und gemeinsame Delivery-Sicherheitsmechanismen.

### Zwischeninkrement 7.1 – Kompaktes QuadWords-Layout

**Status:** abgeschlossen; Issue #17 und PR #18 gemergt.

Vier Boards als kompaktes 2×2-Layout mit stabiler horizontaler Ausrichtung.

### Zwischeninkrement 7.2 – Dynamische Spieler

**Status:** abgeschlossen und real abgenommen.

Dynamische Spielerprofile, historisch stabile Teilnahmezeiträume, Self-Service-/Admin-Commands und Reminder-Opt-out.

Verbindlich: `docs/requirements/dynamic-player-model.md`.

### Inkrement 8 – Tagesstatus und Erinnerungen

**Status:** abgeschlossen und real abgenommen.

Persistente Tagesstatusnachricht, vollständige Seriensemantik, Reminder und idempotente Reconciliation.

Verbindlich: `docs/requirements/daily-status-reminders.md`.

### Inkrement 9 – Produktionsdeployment und Betriebshärtung

**Status:** abgeschlossen; Issue #23 und PR #24 gemergt.

Nicht-Root-Container, PostgreSQL 16, internes Netz, Healthchecks, Backups, Restore, Resume, Rollback und explizites Deployment.

Verbindlich:

- `docs/requirements/production-deployment.md`
- `docs/increments/09-production-deployment-hardening.md`
- `docs/operations/`

### Inkrement 10 – Wochen- und Monatsberichte

**Status:** abgeschlossen und automatisiert vollständig geprüft.

Abgeleitete, idempotent ausgelieferte Wochen- und Monatsberichte mit historischer Teilnehmer- und Seriensemantik.

Verbindlich:

- `docs/requirements/periodic-reports.md`
- `docs/increments/10-periodic-reports.md`
- ADR 0014

### Zwischeninkrement 10.4 – Tagesabschluss und Channel-Bereinigung

**Status:** abgeschlossen; Issue #30 geschlossen und PR #35 gemergt.

Der tägliche Cleanup um 06:00 Uhr finalisiert zuerst den Vortag, pensioniert danach kanonische Ergebnis- und Reminder-Nachrichten und erzeugt beziehungsweise reconciliiert den heutigen Tagesstatus. Reminder laufen standardmäßig um 16:00 und 22:00 Uhr.

Verbindlich:

- `docs/increments/10.4-day-close-reminder-retention-cleanup.md`
- ADR 0015

### Zwischeninkrement 10.5 – Interaktive Ergebnisdetails

**Status:** abgeschlossen; Issue #34 geschlossen und PR #36 gemergt. Die reale Discord-Abnahme wurde gemeinsam mit Zwischeninkrement 10.6 auf dem separaten Testserver durchgeführt.

Tagesstatusnachrichten enthalten spielbezogene Auswahlmenüs für ausschließlich lesende, ephemere GridWords- und QuadWords-Ergebnisdetails. Komponenten sind persistent rekonstruierbar und serverseitig gegen Message-ID, Spieltag, Teilnehmermenge und Optionsseite abgesichert.

Verbindlich: `docs/increments/10.5-interactive-result-details.md`.

### Zwischeninkrement 10.6 – Spielbezogene Teilnahme

**Status:** abgeschlossen; Issue #39 geschlossen und PR #41 gemergt. Automatisierte Prüfung, Produktionscontainer-Gate und reale Discord-Abnahme sind bestanden.

Spieler können unabhängig an GridWords, QuadWords, beiden Spielen oder keinem Spiel teilnehmen. Historische Teilnahmezeiträume werden pro Spieltyp geführt. Reminder, gemeinsame spielbezogene Serien, Tagesstatusmenüs und Berichtsnenner verwenden jeweils die passende Teilnehmermenge. Komplett und perfekt bleiben Zwei-Spiele-Metriken.

Umgesetzt und abgenommen sind insbesondere:

- spielbezogene Domänentypen und PostgreSQL-Zeiträume,
- verlustfreier Backfill bestehender globaler Historie auf beide Spiele,
- atomare `both`-Mutationen und Share-Aktivierung nur des validierten Spieltyps,
- Commands und persönliche Statusprojektionen mit getrennter Teilnahme,
- alle neun Serien auf `G(d)`, `Q(d)`, `U(d)` und `B(d)`,
- eindeutiger Tagesstatus und je Spiel getrennte Ergebnisdetailmenüs,
- Reminder-Audience ausschließlich für teilgenommene fehlende Spiele,
- Wochen- und Monatsberichte mit getrennten Spielnennern,
- Compatibility-Audit und ArchUnit-Grenze gegen globale Teilnahme in produktiven Projektionen,
- reale Discord-Abnahme gemäß `docs/operations/10.6-game-specific-participation-acceptance.md`.

Der finale Stand besitzt 502 grüne Standardtests, eine grüne PostgreSQL-Integrationsmatrix und ein erfolgreiches vollständiges Produktionsimage-, Compose-, Backup-, Restore-, Resume- und Rollback-Gate. Eine GHCR-Veröffentlichung und ein Produktionsdeployment bleiben bewusste separate Releasevorgänge.

Verbindlich:

- `docs/requirements/game-specific-participation.md`
- `docs/increments/10.6-game-specific-participation.md`
- `docs/operations/10.6-game-specific-participation-acceptance.md`
- ADR 0016
- Issue #39

## Feature-complete Versionsbasis

Der Funktionsumfang bis einschließlich Inkrement 10 und der Zwischeninkremente 10.x bildet die feature-complete Basis für Version 1.0.x beziehungsweise 1.1.0.

Neue Inkremente sind keine Voraussetzung für diese Versionsbasis, sondern bewusst priorisierte optionale Produkterweiterungen. Produktive Veröffentlichung und Deployment bleiben von der fachlichen Feature-Vollständigkeit getrennte Freigabeschritte.

## Release Candidate 1.0.0 / 1.1.0

**Status:** Quellumfang implementiert, gemergt, automatisiert geprüft und real abgenommen; manuelle GHCR-Veröffentlichung und Produktionsdeployment erfolgen als getrennte Freigabeschritte.

Ein Release Candidate verwendet eine separate Discord-Testanwendung, einen separaten Testserver/-channel und eine isolierte PostgreSQL-Datenbank. Nach erfolgreicher Abnahme darf ausschließlich der freigegebene Commit veröffentlicht werden. Ein erneuter Workflow-Build gilt als neuer Build dieses Commits; byte-identische Promotion ist nur möglich, wenn das getestete Image erhalten und direkt veröffentlicht wird.

## Obsolet gewordene frühere Roadmap-Platzhalter

Die früher vorgemerkten Folgeinkremente

- „Statistik- und Konfigurations-Commands“ und
- „Regelbasierte Kommentare“ als generischer Sammelumfang

werden nicht weiterverfolgt. Sie waren keine umgesetzten oder abgenommenen Anforderungen. Einzelne Ideen daraus können später nur über neue, ausdrücklich priorisierte Issues mit eigener Fachentscheidung zurückkehren.

## Zur Umsetzung vorgesehen

### Inkrement 11 – Kontextabhängige Ausreden

**Status:** technisch integriert; Paket 8B dokumentiert die Gesamtregression und wartet auf die reale Discord-Abnahme. Issue #42 bleibt bis zu deren Abschluss offen.

Bei seltenen absoluten Ergebnis-, Zeit-, Board- oder bisherigen Tagesausreißern kann der Ergebnisautor freiwillig aus drei ephemeren redaktionellen Ausreden wählen, einmal nach einem Stil neu würfeln oder verzichten. Eine Auswahl wird persistiert und als ausschließlich sichtbarer Ausredentext ohne Stilbezeichnung in dieselbe kanonische Ergebnisnachricht gerendert.

Verbindlich:

- `docs/requirements/excuses.md`
- `docs/increments/11-contextual-excuses.md`
- ADR 0017
- Issue #42

Die Umsetzung erfolgte paketweise über Ausredendomäne und Katalogmechanik, Angebots- und Boardkontext, PostgreSQL-Zustand, einmalige Ergebnisintegration, kanonische Komponenten, ephemere Auswahl, Lifecycle-Härtung und den redaktionellen Vollkatalog. Paket 8B führt die Gesamtregression und die reale Discord-Abnahme gemäß `docs/operations/11-contextual-excuses-acceptance.md` zusammen.

Der Integrationsbranch ist `feature/contextual-excuses`. Das Produktionsfeature bleibt bis zum getrennten Rollout standardmäßig deaktiviert.

## Definition of Done

- Issue- und Paketabnahmekriterien erfüllt,
- Standardbuild grün,
- bei Persistenzumfang PostgreSQL-Profil grün,
- GitHub Actions vollständig grün,
- keine Secrets,
- Dokumentation aktuell,
- notwendiger manueller Discord-Smoke-Test erfolgreich,
- bei Betriebsänderungen Backup-, Restore-, Upgrade- und Rollbackweg geprüft,
- bei Reportänderungen reale Darstellung und Duplikatsicherheit geprüft,
- produktiver Release erst nach erfolgreicher RC-Abnahme.
