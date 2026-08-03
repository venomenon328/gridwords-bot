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

**Status:** automatisiert umgesetzt; Issue #34 geschlossen und PR #36 gemergt. Die reale Discord-Abnahme erfolgt mit dem Release Candidate auf dem separaten Testserver.

Tagesstatusnachrichten enthalten spielbezogene Auswahlmenüs für ausschließlich lesende, ephemere GridWords- und QuadWords-Ergebnisdetails. Komponenten sind persistent rekonstruierbar und serverseitig gegen Message-ID, Spieltag, Teilnehmermenge und Optionsseite abgesichert.

Verbindlich: `docs/increments/10.5-interactive-result-details.md`.

## Release Candidate 1.0.0

**Status:** vorbereitet; lokale Containererstellung und reale Discord-Abnahme stehen aus.

Der RC wird lokal aus dem nach vollständiger CI freigegebenen `main` gebaut. Er verwendet eine separate Discord-Testanwendung, einen separaten Testserver/-channel und eine isolierte PostgreSQL-Datenbank. Der produktive Rollout und die manuelle GHCR-Veröffentlichung erfolgen erst nach erfolgreicher Abnahme und werden separat durchgeführt.

Die Projektversion ist `1.0.0`. Die RC-Bezeichnung gehört zum lokalen Image-Tag und zum Abnahmeprotokoll; dadurch kann exakt derselbe getestete Imageinhalt nach erfolgreicher Abnahme als `1.0.0` veröffentlicht werden.

## Geplante Folgeinkremente

### Inkrement 11 – Statistik- und Konfigurations-Commands

- Read-only-Statistik-Slash-Commands auf derselben Reporting-Grundlage,
- eindeutige Auswahl der Serienarten,
- persistente Zeitkonfiguration,
- Admin-Autorisierung,
- Scheduler-Neuplanung ohne Neustart.

### Inkrement 12 – Regelbasierte Kommentare

- regelbasierte Kategorien und Textvarianten,
- Serien-, Komplett- und Perfekt-Auslöser,
- definierte Nachrichtenlimits,
- keine generative KI.

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
