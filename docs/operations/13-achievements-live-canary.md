# Inkrement 13 – kontrollierte Live-Abnahme

**Stand:** 8. August 2026  
**Bezug:** Issue #94 / PR #103

Nach erfolgreicher technischer Härtung und dem lokalen Discord-Smoke wird der verbleibende manuelle Achievement-Test bewusst als kontrollierter Canary in der Live-Umgebung durchgeführt.

## Bereits lokal abgenommen

Auf separatem Testserver und isolierter PostgreSQL-Datenbank wurden erfolgreich geprüft:

- realer Spring-Boot-Start im `database`-Profil,
- Upgrade auf Migration 024,
- historischer Bootstrap bis `SUCCEEDED`,
- genau eine historische Introduction,
- korrekte rückwirkende Awards für einen vorbereiteten `3/6`-GridWords-Fall,
- vollständige Namen/Beschreibungen/Unicode-Emojis,
- keine sichtbaren Publication-IDs, Recovery-Hashes oder Recovery-URLs,
- `/achievements` Self-Ansicht und Game-Filter,
- Restart ohne doppelte historische Introduction.

Der lokale Smoke hat zwei vor dem Produktivgang behobene reale Randfälle gefunden: Spring-Wiring-Reihenfolge der Achievement-Persistenz und sichtbare Recovery-Metadaten im Discord-Embed.

## Live-Canary nach Merge und Deployment

Der Produktivrollout ist kein Bestandteil von PR #103. Beim separaten Rollout werden unmittelbar nach Start in dieser Reihenfolge geprüft:

1. Anwendung startet ohne Achievement-/Liquibase-/Discord-Wiring-Fehler.
2. `achievement_bootstrap_state` für `achievements-v1` erreicht `SUCCEEDED`.
3. Historische Introductions erscheinen höchstens einmal pro Teilnehmer und ohne technische Metadaten.
4. `/achievements` liefert eine ephemere Self-Ansicht; GridWords-/QuadWords-Filter bleiben korrekt.
5. `/achievement-list` zeigt alle 60 Definitionen vollständig, ephemeral und nur mit `✅`/`❌` als persönlichem Status.
6. Ein regulärer Live-Share, der mehrere neue Achievements freischaltet, erzeugt genau einen aggregierten Live-Batch.
7. Ein Bot-Restart nach synchronisierter Meldung erzeugt kein Announcement-Duplikat.
8. Eine kontrollierte Ergebnis-Korrektur invalidiert den aktuellen Award-State ohne öffentliche Aberkennungsnachricht und ohne bereits publizierte Unlock-Historie zu editieren oder zu löschen.
9. Falls derselbe Nachweis später wieder erfüllt wird, reaktiviert sich der Award ohne erneute öffentliche Unlock-Meldung.

## Abbruch-/Rollbackkriterien

Der Canary wird abgebrochen und der bestehende getestete Rollbackpfad verwendet, wenn insbesondere eines der folgenden Symptome auftritt:

- Anwendung startet nicht oder Bootstrap bleibt durch unbekannten technischen Fehler blockiert,
- doppelte historische Introductions oder doppelte Live-Unlock-Meldungen,
- falsche Teilnehmer-/Scope-Zuordnung,
- öffentliche Revocation bei Korrektur,
- sichtbare interne Publication-/Recovery-Metadaten,
- `/achievements` oder `/achievement-list` schreibt fachliche Daten oder erzeugt öffentliche Antworten,
- Regression bei kanonischer Ergebnisverarbeitung, Records oder Ausreden.

Keine Achievement-Tabellen werden zur Fehlerbehebung manuell umgeschrieben oder gelöscht. Diagnose erfolgt read-only; bei Bedarf wird auf den vorherigen getesteten Release zurückgerollt.
