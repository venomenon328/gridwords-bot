# Abnahmeprotokoll Zwischeninkrement 10.6

Dieses Dokument bündelt die technische Abschlussprüfung und die reale Discord-Abnahme für die spielbezogene Teilnahme aus Issue #39. Es ersetzt keine Requirements oder ADRs, sondern dokumentiert den Nachweis für den freizugebenden Commit.

## Freigaberegel

Der Pull Request bleibt Draft, bis alle automatisierten und manuellen Punkte erfolgreich abgeschlossen und dokumentiert sind. Erst der vollständig abgenommene Commit wird auf **Ready for review** gesetzt. Dadurch startet einmalig der vollständige Container-, Compose-, Backup-, Restore-, Resume- und Rollback-Workflow.

Ein fehlgeschlagener finaler Workflow oder eine nachträgliche Codeänderung macht eine erneute Prüfung des neuen Heads erforderlich. Der manuelle GHCR-Publish bleibt während der Pull-Request-Abnahme übersprungen.

## Compatibility-Entscheidung

Die produktiven Application-, Status- und Reporting-Pfade verwenden ausschließlich `GameParticipationPeriod` und die täglichen Mengen `G(d)`, `Q(d)`, `U(d)` und `B(d)`.

Folgende globale Einstiege bleiben bewusst als eng begrenzte Source- und Test-double-Kompatibilität erhalten:

- `ParticipationPeriodCompatibility`,
- globale Legacy-Methoden in `PlayerStore`,
- `StreakCalculator.calculateWithParticipation(...)`.

Sie sind keine zulässige Grundlage neuer fachlicher Logik. Eine ArchUnit-Regel verhindert Abhängigkeiten von `ParticipationPeriod` in Application-, Status- und Reporting-Projektionen. Vergleichstests sichern die identische Zwei-Spiele-Projektion der Compatibility-Pfade.

## Automatisierte Abschlussprüfung

Auf dem finalen Draft-Head ausführen:

```bash
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Nachzuweisen sind mindestens:

- Standard-, Domain-, Application-, Adapter- und ArchUnit-Tests,
- leeres PostgreSQL-Schema über den vollständigen Liquibase-Pfad,
- Upgrade eines Schemas vor Zwischeninkrement 10.6,
- Backfill offener und geschlossener globaler Zeiträume auf beide Spieltypen,
- Constraints gegen Überlappungen und mehrere offene Zeiträume,
- atomare `both`-Mutation mit vollständigem Rollback beim zweiten Fehler,
- konkurrierende gleiche und unterschiedliche Spielaktivierungen,
- produktives Spring-Wiring mit deaktiviertem Discord,
- Share-, Command-, Serien-, Tagesstatus-, Menü-, Reminder- und Reportregressionen,
- sichere kanonische Veröffentlichung, Korrektur, Recreate und Quelllöschung.

Automatisierter Abschlussstand vor der realen Discord-Abnahme:

- GitHub Actions `standard-build` auf Head `424f1a2`: 502 Tests erfolgreich,
- GitHub Actions `database-integration` auf Head `424f1a2`: erfolgreich,
- normale GitHub-CI vollständig grün,
- Containerworkflow wegen Draft-Status absichtlich übersprungen.

Die Ergebnisse des finalen Container- und Betriebsworkflows werden nach dem Ready-for-review-Gate in PR #41 dokumentiert.

## Reale Discord-Abnahme

**Status:** bestanden  
**Datum:** 3. August 2026  
**Geprüfter Stand:** `424f1a2f5bf5de7575f82a985f826e1deb2177cc`

Testumgebung:

- separate Discord-Testanwendung,
- separater Testserver und Testchannel,
- isolierte PostgreSQL-Datenbank,
- keine Produktivtokens oder Produktivdaten.

Die Abnahme wurde durch den Betreiber anhand des dokumentierten manuellen Testplans durchgeführt und am 3. August 2026 als bestanden bestätigt. Konkrete Discord-Message-IDs und private Serverdaten werden nicht im Repository dokumentiert.

- [x] Bestehender Zwei-Spiele-Spieler ist nach dem Upgrade fachlich unverändert.
- [x] Ein gültiges GridWords-Share eines neuen Spielers aktiviert ausschließlich GridWords.
- [x] Ein gültiges QuadWords-Share eines neuen Spielers aktiviert ausschließlich QuadWords.
- [x] `/participation join` funktioniert für `gridwords`, `quadwords` und `both`; fehlende Auswahl entspricht `both`.
- [x] Der Austritt aus genau einem Spiel wirkt ab morgen und lässt das andere Spiel unverändert.
- [x] Ein globaler Reminder-Opt-out bleibt beim Hinzufügen des zweiten Spiels erhalten.
- [x] Reminder enthalten keinen Spieler in einer Spielzeile, an deren Spiel er nicht teilnimmt.
- [x] Der Tagesstatus unterscheidet eindeutig `— nimmt nicht teil` und `⬜ noch nicht eingereicht`.
- [x] GridWords- und QuadWords-Menüs enthalten jeweils nur ihre historische Teilnehmermenge; Details sind ephemer.
- [x] Nach Neustart wird der Tagesstatus einschließlich Komponenten korrekt rekonstruiert.
- [x] Ein Wochenbericht oder kontrollierter Report-Smoke zeigt getrennte Spielnenner und `Nicht teilgenommen` bei null Spieltagen.
- [x] Korrektur, Replay und Neustart erzeugen keine Duplikate; Originalquellen werden nicht vorzeitig gelöscht.

### Beobachtungsprotokoll

| Punkt | Ergebnis | Beobachtung |
|---|---|---|
| 1–12 | PASS | Betreiberbestätigung nach vollständigem Testlauf auf separatem Test-Discord. |
| Gemeinsame spielbezogene Serien bei nur einem Teilnehmer | PASS | Erwartungsgemäß `0`: eine gemeinsame GridWords- beziehungsweise QuadWords-Lösungsserie benötigt mindestens zwei Teilnehmer des jeweiligen Spiels. |

## Produktionsmigration

Vor jedem Deployment wird über `scripts/deploy.sh` ein validiertes Datenbankbackup erzeugt. Das Deployment darf nur einen unveränderlichen `sha-<40 hex>`-Tag oder den einmalig veröffentlichten SemVer-Tag verwenden.

Die Migration 015 kopiert bestehende globale Teilnahmezeiträume verlustfrei auf GridWords und QuadWords, validiert den Backfill und ersetzt erst danach die alte Tabellenstruktur. Nach dem Start sind mindestens zu prüfen:

- Liquibase ist vollständig und fehlerfrei,
- `player.active` entspricht der heutigen Union-Teilnahme,
- bestehende Zwei-Spiele-Historie liefert unveränderte Status-, Serien- und Reportwerte,
- neue Single-Game-Teilnahme erscheint nur in ihrem Spielpfad.

## Backup und Rollback

App-Rollback und Datenbank-Restore bleiben getrennte Vorgänge.

Ein fehlerhaftes App-Image wird über einen früheren unveränderlichen Tag zurückgerollt:

```bash
cd /opt/gridwords-bot
./scripts/deploy.sh sha-PREVIOUS_40_HEX_COMMIT
```

Ein älteres Image darf nur verwendet werden, wenn es mit dem bereits migrierten Schema kompatibel ist. Andernfalls ist der kontrollierte Datenbank-Restore nach `docs/operations/backup-restore.md` erforderlich. Liquibase-Migrationen werden nicht automatisch rückwärts ausgeführt.

Nach jedem Deployment oder Rollback:

```bash
cd /opt/gridwords-bot
./scripts/verify-deployment.sh
```

## Finales GitHub-Gate

Nach erfolgreicher realer Discord-Abnahme:

1. Abschlussdokumentation und Testzahlen auf dem Branch aktualisieren.
2. Normale Remote-CI im Draft vollständig grün abwarten.
3. PR #41 auf **Ready for review** setzen.
4. `Build and exercise production image` vollständig grün abwarten.
5. Prüfen, dass der manuelle GHCR-Publish übersprungen blieb.
6. Paket 8 und Issue #39 mit belegter vollständiger Abnahme aktualisieren.
7. PR #41 nicht im Rahmen dieses Abnahmeschritts mergen und kein Image veröffentlichen.
