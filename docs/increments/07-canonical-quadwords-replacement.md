# Inkrement 7: Kanonische QuadWords-Konsolidierung und sichere Ersetzung

**Status:** automatisiert umgesetzt; manueller Discord-/PostgreSQL-Smoke-Test offen  
**Issue:** #15  
**Draft-PR:** #16  
**Branch:** `feature/canonical-quadwords-replacement`

## Ziel

Ein sicher geparstes und persistiertes QuadWords-Ergebnis wird in genau einer kanonischen, korrigierbaren Bot-Nachricht dargestellt. Die menschliche Quelle darf erst nach persistent bestätigter Veröffentlichung gelöscht werden.

## Ausgangslage

- Inkremente 0 bis 6 sind abgeschlossen.
- QuadWords besitzt vier normalisierte und persistierte Boards sowie Parser-Version `quadwords-image-v2`.
- GridWords besitzt bereits den vollständigen sicheren Publish-/Edit-/Delete-Ablauf mit Claims, Delivery-Fence, Recovery, Supersession und Duplikatbereinigung.
- Gültige QuadWords-Quellen bleiben derzeit sichtbar und erhalten `✅`.

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
- vollständige GridWords-Regression

## Ausgeführte Validierung

```powershell
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Automatisierte Tests verwenden keinen echten Discord-Token und öffnen keine echte Discord-Verbindung. Docker Desktop darf für PostgreSQL vorausgesetzt werden.

## Manueller Smoke-Test vor Merge

- gelöstes QuadWords: genau ein Embed, danach sichere Quelllöschung
- visueller Vergleich aller vier Boards
- `X/9` innerhalb der Discord-Grenzen
- Korrektur editiert dieselbe Bot-Nachricht
- Neustart und Retry ohne Duplikate
- fachlich ungültiges Bild bleibt mit `⚠️` sichtbar
- fehlende Löschberechtigung und kontrollierte Recovery
- unveränderter GridWords-Ablauf

## Nicht-Ziele

- kein Tagesstatus oder Reminder
- keine Berichte, Commands oder Kommentare
- kein Backfill historischer Channelnachrichten
- kein Redesign des Bildparsers ohne konkreten Fehler
- keine persistente Rohbildablage
