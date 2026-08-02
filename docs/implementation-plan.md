# Implementierungsplan

Dieser Plan zerlegt die Anforderungsspezifikation in kleine, reviewbare Inkremente. Für Serien gilt `docs/requirements/series-model.md`; für dynamische Spieler und Reminder-Teilnahme `docs/requirements/dynamic-player-model.md`; für Tagesstatus und Reminder-Auslieferung `docs/requirements/daily-status-reminders.md`; für periodische Berichte `docs/requirements/periodic-reports.md`; für das Produktionsdeployment `docs/requirements/production-deployment.md`; für lokale Infrastruktur und Datenbanktests `docs/adr/0010-docker-available-local-development.md`.

## Leitprinzipien

- Erst stabiler Build, dann Fachlogik.
- Parser und Regeln bleiben soweit möglich unabhängig von Discord und Datenbank.
- Der schnelle Standardbuild bleibt ohne Docker, PostgreSQL und Discord-Token ausführbar.
- Docker Compose ist die bevorzugte lokale PostgreSQL-Umgebung.
- Persistenzänderungen werden lokal mit `database-integration` und zusätzlich in GitHub Actions geprüft.
- Originalnachrichten werden erst nach vollständig getesteter kanonischer Veröffentlichung gelöscht.
- QuadWords-Bildparser und sichere QuadWords-Konsolidierung werden vor Tagesstatus und Erinnerungen umgesetzt.
- Dynamische Spieler, historische Teilnahmezeiträume und Reminder-Opt-out werden vor dem Reminder-Scheduler umgesetzt.
- Der Produktionsweg baut auf unveränderlichen Containerimages, serverseitigen Secrets, Backups vor Updates und expliziten Deployments auf.
- Periodische Berichte bauen auf abgeleiteten Kennzahlen, einem expliziten Periodenend-Stichtag und persistenter idempotenter Delivery auf.
- Große Inkremente werden in kleine, einzeln reviewbare und jeweils vollständig getestete Terra-Pakete zerlegt.

## Inkremente 0 bis 5

### Inkrement 0 – Grundgerüst

**Status:** abgeschlossen, PR #1 gemergt.

Java 21, Spring Boot, JDA, Maven, externe Konfiguration, infrastrukturunabhängiger Build und Gateway-Smoke-Test.

### Inkrement 1 – Share-Textparser

**Status:** abgeschlossen, PR #4 gemergt.

Deterministische GridWords-/QuadWords-Kopfzeilen, GridWords-Raster, Ergebnis, Dauer, Datum und Flamme.

### Inkrement 2 – Persistenzmodell

**Status:** abgeschlossen, PR #6 gemergt.

Liquibase, PostgreSQL-Adapter, Submission-Zustände, Idempotenz und Datenbankintegrationsprofil.

### Inkrement 3 – Discord-Inbound

**Status:** abgeschlossen, PR #8 gemergt.

Gefilterter Listener, begrenzter Executor, Parse/Persistenz, `✅` und `⚠️`, noch keine Ersetzung.

### Inkrement 4 – Kanonische GridWords-Nachricht

**Status:** abgeschlossen, PR #10 gemergt; Smoke-Test am 29. Juli 2026 erfolgreich.

Kanonisches Embed, Serien, persistierte Bot-Message-ID, Korrektur-Edit, Claims, Recovery und Duplikatbereinigung.

### Inkrement 5 – Sichere GridWords-Ersetzung

**Status:** abgeschlossen, PR #12 gemergt; vollständiger Smoke-Test am 30. Juli 2026 erfolgreich.

Quelllöschung erst nach persistierter kanonischer Veröffentlichung, Lease-/Token-Claims, Retry, Startup-Recovery, permanente Fehler und gezielte Superseded-Reconciliation.

## Inkrement 6 – QuadWords-Bildparser

**Status:** abgeschlossen, PR #14 gemergt; lokale Standard- und PostgreSQL-Tests sowie vollständiger Discord-/PostgreSQL-Smoke-Test am 30. Juli 2026 erfolgreich.

**Ziel:** QuadWords-Ergebnisbilder ohne OCR sicher in vier normalisierte Raster überführen und persistent speichern.

Umgesetzt:

- transportneutrale Attachment-Referenz aus Channel-, Message- und Attachment-ID
- schmaler `AttachmentContentLoader`-Port und JDA-Adapter
- Download erst nach erfolgreicher Kopfzeilenprüfung und eindeutiger Bildauswahl
- Download der originalen signierten Discord-CDN-Datei statt der möglicherweise transformierten Medienproxy-Variante
- Download und Decode außerhalb des JDA-Event-Threads
- reine Java-Bildverarbeitung mit `ImageIO` und `BufferedImage`
- Unterstützung von PNG und JPEG
- stabile Ablehnung nicht unterstützter oder beschädigter Formate
- 8-MiB-, 4096×4096- und 12-Megapixel-Grenzen
- Erkennung einer 2×2-Anordnung mit genau fünf Spalten je Board
- kanonische Reihenfolge `Oben links`, `Oben rechts`, `Unten links`, `Unten rechts`
- robuste Flächenstichproben für `⬜`, `🟨`, `🟩`
- kontrollierte Fehler für Geometrie, Struktur, Zeilenzahl und Farbunsicherheit
- Normalisierung klar fehlender nachlaufender Zeilen als Leerzellen
- typisiertes Domänenmodell `QuadWordsBoards`/`QuadWordsBoard`
- Parser-Version `quadwords-image-v2`
- Liquibase-Persistenz aller vier Boards und Parser-Version
- Kompatibilität mit boardlosen `quadwords-share-v1`-Ergebnissen
- technischer Pre-Result-Retry über `FAILED_RETRYABLE`
- Golden-Tests für alle freigegebenen PNG-Fixtures
- synthetische Tests für Skalierung, Ränder, JPEG, beschädigte Dateien, unsichere Farben und Ressourcenlimits
- Application-, Discord-Adapter-, Architektur- und PostgreSQL-Integrationstests
- keine Rohbildpersistenz: Bytes werden nur im Arbeitsspeicher verarbeitet

Discord-Verhalten:

- sicher geparstes und gespeichertes QuadWords: Original bleibt sichtbar, `✅`
- stabil fachlich ungültiges Bild: Original bleibt sichtbar, `⚠️`
- technischer Attachmentfehler: Original bleibt sichtbar, keine irreführende Reaktion, retryfähig
- GridWords bleibt unverändert
- keine kanonische QuadWords-Nachricht und keine QuadWords-Quelllöschung

Abnahme:

- lokaler Standardbuild: 181 Tests grün
- lokales PostgreSQL-Profil: 51 Integrationstests zusätzlich grün
- beide CI-Jobs grün
- reales gelöstes und reales `X/9` visuell und in PostgreSQL geprüft
- fachliche Fehlerfälle, Korrektur und Neustart erfolgreich geprüft
- GridWords-Regressionsprüfung erfolgreich

## Inkrement 7 – Kanonische QuadWords-Konsolidierung und sichere Ersetzung

**Status:** abgeschlossen, PR #16 gemergt; lokale Vollbuilds und vollständiger realer Discord-/PostgreSQL-Smoke-Test am 30. Juli 2026 erfolgreich.

**Ziel:** QuadWords-Text und vier normalisierte Boards in genau eine korrigierbare Bot-Nachricht überführen und die Quelle danach sicher löschen.

Umgesetzter Umfang:

- kanonische Darstellung aller vier Boards sowie Spieler, Datum, Ergebnis, Dauer und verbindliche Serien
- genau eine persistierte Bot-Message-ID je Spieler, Spieltyp und Spieltag
- Korrektur durch Edit derselben Bot-Nachricht mit Erhalt bereits etablierter Kontextzeilen
- spieltypbezogener Publication-Key
- gemeinsame, begrenzt generalisierte Claims, Delivery-Fence, Retry-, Startup-Recovery-, Supersession- und Duplikatbereinigung für GridWords und QuadWords
- Quelllöschung erst nach persistierter kanonischer Veröffentlichung sowie Delete-Recovery
- explizit nicht publizierbare boardlose `quadwords-share-v1`-Ergebnisse: kein Discord-Aufruf, Delete-Handoff, Erfolgssignal oder Refresh-Hot-Loop
- PublicationContext auch dann genau einmal, wenn QuadWords als zweite Einreichung persönliche und gemeinsame Komplett-/Perfektzustände etabliert
- kein QuadWords-`✅` nach erfolgreicher Konsolidierung
- permanente Löschfehler ohne Scheduler- oder Hot-Loop sowie kontrollierte Wiederaufnahme bei Neustart oder nach einer späteren bestätigten Veröffentlichung desselben Ergebnisses
- parametrisierte gemeinsame Sicherheitsfälle statt einer kopierten GridWords-Zustandsmaschine

Abnahme:

- `mvn --batch-mode --no-transfer-progress clean verify`: 196 Tests grün
- `mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify`: 196 Standardtests plus 56 PostgreSQL-Integrationstests grün
- GitHub Actions vollständig grün
- echte GridWords- und QuadWords-Erstveröffentlichung sowie Korrektur geprüft
- alle vier QuadWords-Boards visuell geprüft
- sichere Löschung, fachliche Ablehnung, Neustart und Duplikatschutz geprüft
- permanenter Löschfehler ohne Hot-Loop und kontrollierte Recovery nach Berechtigungswiederherstellung geprüft

## Zwischeninkrement 7.1 – Kompaktes 2×2-Layout der QuadWords-Grids

**Status:** abgeschlossen, Issue #17 und PR #18 gemergt; visueller Discord-Smoke-Test am 30. Juli 2026 erfolgreich.

**Ziel:** die vier bereits korrekt publizierten QuadWords-Grids näher an der ursprünglichen Spielanordnung und kompakter darstellen.

Umgesetzt:

- `topLeft` und `topRight` nebeneinander
- `bottomLeft` und `bottomRight` darunter nebeneinander
- keine sichtbaren Positionslabels
- sichtbare Einzelhöhe endet bei der ersten vollständig grünen Lösungszeile; ungelöste Boards behalten alle Zeilen
- Paarhöhe entspricht dem längeren Board des jeweiligen horizontalen Paares
- dunkle geometrisch stabile Platzhalterzellen für die Ausrichtung kürzerer Boards
- Monospace-Codeblock für GridWords und QuadWords
- Wiederherstellung historisch etablierter Komplett-/Perfektzeilen bei kanonischer Neuerzeugung
- Titel, Ergebnis, Dauer, Serien, Publication-Key und Korrektur-Edit unverändert
- Parser-, Persistenz- und Publish-/Delete-Zustandsmaschine unverändert

Abnahme:

- 199 Standardtests grün
- 57 PostgreSQL-Integrationstests grün
- echte Discord-Ausrichtung, GridWords-Codeblock, Kontextzeilen und sichere Löschung erfolgreich geprüft

## Zwischeninkrement 7.2 – Dynamische Spieler, Teilnahmezeiträume und Reminder-Opt-out

**Status:** abgeschlossen; automatisierte und reale Discord-/PostgreSQL-Abnahme am 30. Juli 2026 erfolgreich.

**Ziel:** die feste Zwei-Spieler-Konfiguration durch dynamische Spielerprofile und historisch stabile Teilnahmezeiträume ersetzen sowie die Reminder-Präferenzen für Inkrement 8 vorbereiten.

Umgesetzt:

- jeder menschliche Nutzer im Zielchannel kann durch ein vollständig gültiges Share Spieler werden
- ungültige Shares und normale Texte erzeugen kein Spielerprofil
- serverbezogener Discord-Anzeigename und extern konfigurierter Administratorstatus werden bei Share und Commands synchronisiert
- `player.active` als aktueller Zustand und datierte, nicht überlappende Teilnahmezeiträume
- Spielerregistrierung, Aktivierung, Ergebnis und PublicationContext werden atomar gespeichert
- erstmalige konkurrierte Registrierungen werden vor der Periodenmutation serialisiert
- automatische Aktivierung ab fachlichem Share-Spieltag
- `/participation join|leave|status` als Self-Service
- `/player activate|deactivate|status` für konfigurierte Administratoren
- `/reminders on|off|status` unabhängig vom Teilnahmezustand
- ephemere Antworten und Statusausgabe mit laufendem Teilnahmezeitraum
- gemeinsamer Komplett-/Perfekttag über alle am jeweiligen Tag aktiven Spieler, mindestens zwei Teilnehmer
- historische Serien bleiben bei späterem Beitritt oder Austritt unverändert
- transportneutrale Reminder-Audience mit fehlenden Spieltypen, Discord-User-ID und getrenntem Mentionstatus
- Entfernung der festen `PLAYER_1_*`-/`PLAYER_2_*`-Konfiguration
- Liquibase-Migration und Backfill für bestehende Spieler
- produktiver Spring-/PostgreSQL-Startup mit genau einem dynamischen Persistenzadapter
- vollständige GridWords-/QuadWords-Publish-, Edit-, Delete-, Recovery- und Parser-Regression

Verbindlich: `docs/requirements/dynamic-player-model.md` und `docs/increments/07b-dynamic-player-participation.md`.

Abnahme:

- 207 Standardtests lokal und in GitHub Actions grün
- 63 PostgreSQL-Integrationstests lokal und in GitHub Actions grün
- keine automatisierte Discord-Verbindung oder Token-Verwendung
- realer Discord-/PostgreSQL-Smoke-Test mit mindestens drei Nutzern erfolgreich
- automatische Registrierung, Namenssynchronisierung, Commands, Reminder-Opt-out, wechselnde Teilnehmer und sichere Ergebnisersetzung geprüft

## Inkrement 8 – Tagesstatus, vollständige Serien und Erinnerungen

**Status:** abgeschlossen; automatisierte und reale Discord-/PostgreSQL-Abnahme am 31. Juli 2026 erfolgreich.

**Ergebnis:** Der tägliche Kernnutzen basiert vollständig auf der historisch wirksamen dynamischen Teilnehmermenge.

Umgesetzt:

- genau eine persistente Tagesstatusnachricht pro Guild, Channel und Spieltag
- Status-Create beim ersten Ergebnis oder spätestens beim ersten fälligen Reminder
- Edit derselben Message-ID nach Ergebnissen, Korrekturen und heute wirksamen Aktivierungen
- kontrollierte Neuerzeugung und Duplikatbereinigung nach externer Löschung oder unklarem Discord-Ausgang
- alle fünf persönlichen Serien sowie gemeinsame Komplett- und Perfektserie
- vorläufige Semantik ausschließlich für heute; historische Tage einschließlich gestern werden endgültig berechnet
- vollständige Rekonstruktion nach zulässigen Vortagsnachträgen
- Reminder um 18:00 und 23:00 Uhr mit vollständiger Audience aller aktiven unvollständigen Spieler
- Reminder-Opt-out bei neuer beziehungsweise erneuter Aktivierung
- reine Text-Reminder mit verlinkten Spielnamen
- echte ID-basierte Mentions ausschließlich für Opt-ins; Klartextnamen für Opt-outs
- `X/6` und `X/9` gelten als eingereicht
- strikt begrenzte Allowed Mentions
- persistierter No-op, erneute Audience-Bewertung, Supersession und Ablauf vergangener Stufen
- Startup-Reconciliation für heute und gestern sowie DST-sichere Zeitberechnung
- tokengebundene Claims/Leases, Fingerprints, Retry-Backoff, permanente Zustände und Crash-Recovery
- keine sichtbaren technischen Delivery-Schlüssel
- keine Discord-I/O innerhalb von Datenbanktransaktionen

Abnahme:

- 249 Standardtests grün
- 76 PostgreSQL-Integrationstests grün
- vollständige Spring-, JDA-, Liquibase-, Konkurrenz-, Recovery- und Ergebnisregression
- keine echte Discord-Verbindung und kein Token in automatisierten Tests
- realer Discord-/PostgreSQL-Smoke-Test einschließlich gezieltem UX-Nachtest erfolgreich

Verbindlich: `docs/requirements/daily-status-reminders.md`, `docs/increments/08-daily-status-reminders.md`, ADR 0012 und Issue #21.

## Inkrement 9 – Reproduzierbares Produktionsdeployment und Betriebshärtung

**Status:** abgeschlossen; Issue #23 geschlossen und PR #24 am 1. August 2026 gemergt.

**Ergebnis:** Die Kernversion läuft reproduzierbar und gehärtet auf einem Netcup VPS 500 G12 unter Debian 13.

Umgesetzt:

- privates GHCR-Image mit unveränderlichen SHA- und expliziten Release-Tags,
- Multi-Stage-Java-21-Image mit Nicht-Root-Runtime, Healthchecks und begrenzter JVM,
- Docker Compose mit Bot und PostgreSQL im internen Netz,
- keine PostgreSQL- oder Actuator-Hostports,
- serverseitige Secrets und frische PostgreSQL-Datenbank,
- separate Discord-Produktionsanwendung,
- idempotentes Deploy-/Verify-/App-Rollback-Skript,
- atomare Backup-/Restore-Skripte mit 14 täglichen und 8 wöchentlichen Generationen,
- Debian-13-Bootstrap-, Deployment-, Backup- und Troubleshooting-Handbuch,
- vollständiger automatisierter Container-, Backup-, Restore-, Upgrade- und Rollback-Vollpfad.

Automatisierte Abnahme:

- 253 Standardtests grün,
- 76 PostgreSQL-Integrationstests grün,
- Imagebau, Nicht-Root- und Secretfreiheitsprüfung grün,
- leeres Volume, Liquibase, Healthchecks und Neustart grün,
- Backup, separater Restore, Retention, Upgrade und Rollback grün,
- Fortsetzung nach Unterbrechung, Zustandsreconciliation und No-op grün.

Reale Produktionsabnahme:

- gehärteter Debian-13-Host,
- authentifizierter privater GHCR-Pull,
- frische Datenbank und Liquibase-Startup,
- separate Discord-Produktionsanwendung,
- Bot und PostgreSQL gesund,
- Nachrichtenverarbeitung im Produktionschannel erfolgreich,
- öffentlich weiterhin ausschließlich SSH.

Reminder über reguläre Fälligkeiten sowie Restart-, Restore- und Rollbackverhalten werden im laufenden Betrieb beziehungsweise bei realem Anlass beobachtet und nicht künstlich als Mergeblocker erzwungen.

Verbindlich: `docs/requirements/production-deployment.md`, `docs/increments/09-production-deployment-hardening.md`, ADR 0013, Issue #23 und PR #24.

## Inkrement 10 – Abgeleitete Wochen- und Monatsberichte

**Status:** implementiert; Issue #25 geschlossen und PR #26 gemergt. Der reale Discord-Smoke-Test für Wochen- und Monatsberichte bleibt als separate offene Abnahme dokumentiert.

**Ziel:** Der produktive Bot veröffentlicht idempotente Berichte über die vollständig abgeschlossene Vorwoche und den vollständig abgeschlossenen Vormonat. Alle Kennzahlen bleiben aus Ergebnissen und historischen Teilnahmezeiträumen abgeleitet.

Verbindliche Entscheidungen:

- Wochenbericht montags um 08:00,
- Monatsbericht am Monatsersten um 08:15,
- `Europe/Berlin` und expliziter Periodenend-Stichtag,
- alle Spieler mit mindestens einem Teilnahmetag in der Periode,
- individuelle Nenner nur aus Teilnahmetagen,
- gemeinsame Nenner nur aus Tagen mit mindestens zwei aktiven Spielern,
- persönliche Spiel-, Tages- und Serienwerte,
- beide gemeinsamen Serien,
- Serienstand und Allzeitrekord bis Periodenende,
- keine Gewinnerlogik, Ranglisten oder Mentions,
- deterministische Mehrseiten-Pagination,
- persistente Delivery mit Claim, Lease, Retry, Fingerprint und geordneten Message-IDs,
- 72 Stunden Wochen- und sieben Tage Monats-Catch-up,
- erfolgreiche Berichte bleiben Snapshots,
- keine persistierten Statistik-Snapshots als zweite fachliche Wahrheit.

Umsetzungspakete:

0. Fachliche Grundlage und Projektstatus
1. Perioden- und Reportdomäne
2. Teilnehmer und mögliche Tage
3. Spielbezogene Periodenstatistiken
4. Tagesmerkmale und Serien-Snapshots
5. Gemeinsamer Reporting-Use-Case
6. Discord-Renderer und Pagination
7. Persistente Report-Delivery
8. Wochenbericht und Scheduler
9. Monatsbericht und Scheduler
10. Gesamtintegration und Produktionsfreigabe

Jedes Paket endet mit einem kompilierenden Commit und den einschlägigen Tests. Terra erhält immer nur den nächsten Paketauftrag.

Verbindlich: `docs/requirements/periodic-reports.md`, `docs/increments/10-periodic-reports.md`, ADR 0014 und Issue #25.

## Zwischeninkremente zu Inkrement 10

### Zwischeninkrement 10.1 – QuadWords ohne Bild

**Status:** abgeschlossen; Issue #27 geschlossen und PR #31 gemergt.

Ein gültiger QuadWords-Share ohne Bild ist ein vollständiges Ergebnis; der reale Desktop-Share-Smoke-Test ist auf die produktive Erstverwendung verschoben.

### Zwischeninkrement 10.2 – Gemeinsame spielbezogene Lösungsserien

**Status:** abgeschlossen; Issue #28 geschlossen und PR #32 gemergt.

Die gemeinsamen GridWords- und QuadWords-Lösungsserien sind neben der gemeinsamen Komplett- und Perfektserie transportneutral berechnet und im Tagesstatus dargestellt. Der reale Discord-Smoke-Test ist auf die produktive Erstverwendung verschoben.

### Zwischeninkrement 10.3 – Eigenständiger persönlicher Status-Command

**Status:** in Umsetzung; Issue #29 und Draft-PR #33 auf `feature/personal-status-command`.

Der parameterlose Root-Command `/status` trennt die persönliche Statusanzeige von `/participation`, zeigt ausschließlich die Daten des aufrufenden Nutzers und verbindet Teilnahmestatus sowie Reminder-Opt-in mit den jeweils letzten gültigen Einreichungen.


## Inkrement 11 – Statistik- und Konfigurations-Commands

- Read-only-Statistik-Slash-Commands auf derselben Reporting-Grundlage,
- eindeutige Auswahl der sieben Serienarten,
- danach getrennt persistente Zeitkonfiguration,
- Admin-Autorisierung,
- Scheduler-Neuplanung ohne Neustart.

## Inkrement 12 – Regelbasierte Kommentare

- regelbasierte Kategorien und Textvarianten,
- Serien-, Komplett- und Perfekt-Auslöser,
- definierte Nachrichtenlimits,
- keine generative KI.

## Bewusste Reihenfolge

- Persistenzzustände vor automatischer Löschung,
- GridWords-Ersetzung vor QuadWords-Ersetzung,
- QuadWords-Bildparser vor QuadWords-Konsolidierung,
- sichere Konsolidierung beider Spiele vor Tagesstatus und Erinnerungen,
- kompaktes QuadWords-Layout als isoliertes Renderer-Polish vor dynamischen Spielern,
- dynamische Teilnahmezeiträume und Reminder-Opt-out vor Tagesstatus und Scheduler,
- Produktionshärtung und reproduzierbarer Betrieb vor Berichten und Komfortfunktionen,
- robuste Serienlogik vor Berichten und Kommentaren,
- gemeinsamer Reporting-Kern vor Statistik-Commands,
- Read-only-Statistik vor schreibender Zeitkonfiguration,
- Kommentare zuletzt.

## Definition of Done

- Issue-Abnahmekriterien erfüllt,
- lokaler Standardbuild grün,
- bei Persistenzumfang lokales Datenbankprofil mit Docker grün,
- GitHub Actions vollständig grün,
- keine Secrets,
- Dokumentation aktualisiert,
- notwendiger manueller Smoke-Test erfolgreich,
- bei Betriebsinkrementen zusätzlich Backup-/Restore-/Upgrade-/Rollback-Weg angemessen geprüft,
- bei Reportinkrementen beide Berichtstypen real dargestellt und duplikatsicher geprüft,
- PR reviewbar, Draft-Status erst nach vollständiger Abnahme aufheben.
