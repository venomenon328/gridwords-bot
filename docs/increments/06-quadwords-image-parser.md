# Inkrement 6: QuadWords-Bildparser

**Status:** vorbereitet  
**Issue:** #13  
**Branch:** `feature/quadwords-image-parser`

## Ziel

QuadWords-Ergebnisbilder werden ohne OCR geometrisch und farbbasiert in vier transportneutrale, normalisierte Raster überführt und zusammen mit der Parser-Version persistent gespeichert.

## Verbindlicher Umfang

- schmale transportneutrale Attachment-Referenz und Byte-Download-Grenze
- reiner Parser ohne JDA, Spring, Datenbank, Netzwerk, OCR, ML oder Laufzeit-LLM
- vier Boards in der Reihenfolge `Oben links`, `Oben rechts`, `Unten links`, `Unten rechts`
- robuste Klassifikation in `⬜`, `🟨`, `🟩`
- sichere Struktur-, Plausibilitäts- und Konfidenzprüfung
- transportneutrales QuadWords-Board-Domänenmodell mit Konstruktorinvarianten
- Persistenz der vier Boards und Parser-Version über Liquibase
- stabile Trennung fachlicher Bildfehler von transienten Downloadfehlern
- Originalnachricht bleibt sichtbar; gültiges QuadWords behält `✅`, fachlich ungültiges Bild erhält `⚠️`
- lokaler Standardbuild und lokales PostgreSQL-Integrationsprofil mit Docker

## Ausdrückliche Nicht-Ziele

- keine kanonische QuadWords-Bot-Nachricht
- keine QuadWords-Quelllöschung
- kein Tagesstatus und keine Erinnerungen
- keine Berichte, Slash-Commands oder Kommentare
- kein OCR und keine KI

Die vollständigen Anforderungen, Tests und Abnahmekriterien stehen in GitHub-Issue #13. Der Implementierungsplan bleibt maßgeblich; dieses Dokument dient als Branch-Einstiegspunkt für den vorbereiteten Draft-PR.
