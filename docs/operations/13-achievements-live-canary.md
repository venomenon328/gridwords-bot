# Inkrement 13 – kontrollierte Live-Abnahme

**Stand:** 8. August 2026  
**Bezug:** Issue #94 / PR #103  
**Release:** 1.4.0  
**Produktiver Merge-Commit:** `213fe15dcc59e46856ea9be7066161fdc473353a`

## Status

**Produktivdeployment abgeschlossen. Der ausgeführte Live-Smoke/Canary ist bestanden.**

Während des Rollouts wurde kein Abbruch- oder Rollbackkriterium ausgelöst. Der produktive Bot läuft mit dem Achievement-Schema aus Migration 024 und `achievements-v1`.

## Vor dem Produktivgang lokal abgenommen

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

## Produktiv-Smoke

Der anschließende Rollout auf Release 1.4.0 wurde erfolgreich durchgeführt. Der ausgeführte Smoke-Test bestätigte den produktiven Start und die Achievement-Funktion im echten Discord-/PostgreSQL-Betrieb.

Zum produktiven Funktionsumfang gehören insbesondere:

1. erfolgreicher Anwendungstart ohne Achievement-/Liquibase-/Discord-Wiring-Fehler,
2. persistenter Bootstrap von `achievements-v1`,
3. historische Introductions ohne technische Recovery-Metadaten,
4. ephemere Achievement-Commands,
5. `/achievement-list` als vollständige self-only 60er-Katalogansicht mit ausschließlich `✅`/`❌`,
6. aggregierte Live-Unlocks,
7. korrekturfähiger Award-State ohne öffentliche Aberkennungsnachrichten,
8. Retry-/Restart-/Send-vor-ACK-Recovery über persistente Claims und Message-Identität.

Die tieferen Konkurrenz-, Restart-, Korrektur-, Invalidierungs-, Reaktivierungs- und Send-vor-ACK-Szenarien bleiben zusätzlich durch die in `13-achievements-acceptance.md` dokumentierten echten PostgreSQL-/Gateway-Tests abgesichert. Es werden keine manuellen Produktionsdatenänderungen zur Simulation solcher Fehlerfälle vorgenommen.

## Beobachtung nach Rollout

Im normalen Betrieb sind insbesondere folgende Symptome weiterhin als Incident-/Rollbacksignal zu behandeln:

- doppelte historische Introductions oder doppelte Live-Unlock-Meldungen,
- Bootstrap bleibt durch unbekannten technischen Fehler blockiert,
- falsche Teilnehmer-/Scope-Zuordnung,
- öffentliche Revocation bei Korrektur,
- sichtbare interne Publication-/Recovery-Metadaten,
- `/achievements` oder `/achievement-list` erzeugt Schreibseiteneffekte oder öffentliche Antworten,
- Regression bei kanonischer Ergebnisverarbeitung, Records oder Ausreden.

Keine Achievement-Tabellen werden zur Fehlerbehebung manuell umgeschrieben oder gelöscht. Diagnose erfolgt read-only; bei Bedarf wird der getestete allgemeine Rollback-/Restorepfad verwendet.
