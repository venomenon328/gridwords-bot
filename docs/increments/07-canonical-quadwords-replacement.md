# Inkrement 7: Kanonische QuadWords-Konsolidierung und sichere Ersetzung

**Status:** abgeschlossen; PR #16 gemergt und vollständig abgenommen

**Issue:** #15

**PR:** #16

**Merge-Commit:** `6aa3713701f64b9e6bb372b673fd3d37bc7f0eef`

## Ziel

Ein sicher geparstes und persistiertes QuadWords-Ergebnis wird in genau einer kanonischen, korrigierbaren Bot-Nachricht dargestellt. Die menschliche Quelle darf erst nach persistent bestätigter Veröffentlichung gelöscht werden.

## Ausgangslage

- Inkremente 0 bis 6 waren abgeschlossen.
- QuadWords besaß vier normalisierte und persistierte Boards sowie Parser-Version `quadwords-image-v2`.
- GridWords besaß bereits den vollständigen sicheren Publish-/Edit-/Delete-Ablauf mit Claims, Delivery-Fence, Recovery, Supersession und Duplikatbereinigung.
- Vor diesem Inkrement blieben gültige QuadWords-Quellen sichtbar und erhielten `✅`.

## Umgesetzter Umfang

- kanonischer QuadWords-Renderer mit allen vier Boards
- Ergebnis, Dauer und verbindliche Serienanzeige
- genau eine Bot-Nachricht je Spieler, Spieltyp und Spieltag
- Korrektur durch Edit derselben Bot-Message-ID
- stabiler spieltypbezogener Publication-Key
- sichere Wiederverwendung beziehungsweise kleine Generalisierung der GridWords-Publication- und Deletion-Mechanismen
- Quelllöschung erst nach persistierter Veröffentlichung
- Recovery bei Neustart, Retry und unklarem Discord-Ausgang
- begrenzte Bereinigung älterer supersedierter Quellen
- kein Publish oder Delete für fachlich abgelehnte Bilder oder boardlose `quadwords-share-v1`-Ergebnisse
- Wegfall der `✅`-Reaktion für erfolgreich konsolidierte QuadWords-Quellen
- PublicationContext auch bei QuadWords als zweiter Einreichung; Korrekturen etablieren Zustände nicht erneut und erhalten bestehende Kontextzeilen beim Edit
- expliziter `NOT_PUBLISHABLE`-Ausgang für boardlose Legacy-Ergebnisse ohne Discord-Aufruf, Delete-Handoff, Erfolgssignal oder Refresh-Hot-Loop
- permanente Löschfehler ohne Scheduler- oder Hot-Loop; kontrollierte Reaktivierung genau bei Neustart oder nach einer späteren bestätigten Veröffentlichung desselben Ergebnisses
- parametrisierte gemeinsame Sicherheitsfälle für Erstpublish, Edit, Retry-Handoff, Delivery-Fence und Superseded-Reconciliation
- QuadWords-spezifische Startup-, Legacy-, Delete-Recovery- und Permanentfehlerfälle
- vollständige GridWords-Regression

## Ausgeführte Validierung

```powershell
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Ergebnisse vom 30. Juli 2026:

- Standardbuild: 196 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen
- Datenbankprofil: 196 Standardtests und 56 PostgreSQL-Integrationstests, jeweils 0 Fehler, 0 Fehlschläge, 0 übersprungen
- beide GitHub-Actions-Jobs grün
- PostgreSQL-Vollpfad: bildgestütztes QuadWords bis `COMPLETED`, Korrektur derselben kanonischen Message-ID, einmaliger PublicationContext, Legacy-Schutz, permanenter Delete-Fehler ohne Hot-Loop und kontrollierte Recovery für beide Spieltypen

Automatisierte Tests verwenden keinen echten Discord-Token, keine `.env` und öffnen keine echte Discord-Verbindung. Docker Desktop wird ausschließlich für PostgreSQL-Testcontainers verwendet.

## Manueller Discord-/PostgreSQL-Smoke-Test

Am 30. Juli 2026 erfolgreich geprüft:

- gelöstes QuadWords erzeugt genau ein Embed und löscht danach sicher die Quelle
- alle vier Boards wurden visuell mit dem realen Ergebnisbild verglichen
- `X/9` liegt innerhalb der Discord-Grenzen
- Korrektur editiert dieselbe Bot-Nachricht
- Neustart und Retry erzeugen keine Duplikate
- fachlich ungültiges Bild bleibt mit `⚠️` sichtbar
- fehlende Löschberechtigung führt ohne Hot-Loop zu `PERMANENT`
- nach Wiederherstellung der Berechtigung erfolgt kontrollierte Recovery bis `COMPLETED`
- GridWords-Ablauf bleibt unverändert

## Nachfolge

Das rein visuelle Zwischeninkrement 7.1 aus Issue #17 ordnet die vier QuadWords-Grids kompakt als 2×2-Raster an. Parser, Persistenz, Publication und sichere Löschung bleiben dabei unverändert.

## Nicht-Ziele

- kein Tagesstatus oder Reminder
- keine Berichte, Commands oder Kommentare
- kein Backfill historischer Channelnachrichten
- kein Redesign des Bildparsers ohne konkreten Fehler
- keine persistente Rohbildablage
